package com.piku.client.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.piku.client.data.local.AppDatabase
import com.piku.client.data.local.FavoriteDao
import com.piku.client.data.local.FavoriteEntity
import com.piku.client.data.local.FavoriteFolderDao
import com.piku.client.data.local.FavoriteFolderEntity
import com.piku.client.data.local.FavoriteMembershipEntity
import com.piku.client.data.local.HistoryDao
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.local.toSyncWork
import com.piku.client.data.remote.WebDavClient
import com.piku.client.data.remote.WebDavException
import com.piku.client.domain.model.FavoriteSyncData
import com.piku.client.domain.model.SyncFolder
import com.piku.client.domain.model.SyncMembership
import com.piku.client.domain.model.SyncTombstone
import com.piku.client.domain.model.WorkKey
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.BackupWork
import com.piku.client.domain.source.SourceContentBackup
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

enum class SyncState {
    IDLE, SYNCING, SUCCESS, FAILED
}

enum class TestConnectionState {
    IDLE, TESTING, SUCCESS, FAILED
}

data class SyncResult(
    val state: SyncState,
    val backedUpWorks: Int = 0,
    val error: String? = null,
)

/**
 * 同步配置读取结果：缺少配置时不进入协程，UI 立即看到 FAILED 而不是默默丢错。
 */
private sealed interface SyncConfig {
    data class Ready(val url: String, val username: String, val password: String) : SyncConfig
    data class Missing(val reason: String) : SyncConfig
}

/**
 * 内容备份在 WebDAV 上的路径。源进路径：各源 id 空间互不相通，
 * 不同源的同一个 workId 必须落到不同文件，否则后写的那份会把前一份顶掉。
 */
internal object WebDavBackupPath {
    fun image(folderName: String, key: WorkKey, index: Int, ext: String): String =
        "piku/${folderSegment(folderName)}/image/${stem(key)}_$index.$ext"

    fun text(folderName: String, key: WorkKey): String =
        "piku/${folderSegment(folderName)}/text/${stem(key)}.txt"

    private fun stem(key: WorkKey): String = "${key.source.name}_${key.workId}"

    private fun folderSegment(folderName: String): String {
        val cleaned = folderName.map { if (it.isISOControl() || it in UNSAFE) '_' else it }
            .joinToString("")
        return if (cleaned == "." || cleaned == "..") "_" else cleaned
    }

    private val UNSAFE = charArrayOf('/', '\\', '?', '#', '%')
}

/** 从图片 URL 猜扩展名，认不出就按 jpg */
private fun imageExtension(url: String): String {
    val ext = url.substringAfterLast('.', missingDelimiterValue = "")
        .substringBefore('?')
        .lowercase()
    return if (ext.length in 1..5 && ext.all { it.isLetterOrDigit() }) ext else "jpg"
}

internal enum class RemoteVerdict { USABLE, UNUSABLE, FROM_NEWER }

internal fun remoteVerdict(remoteVersion: Int, currentVersion: Int): RemoteVerdict = when {
    remoteVersion > currentVersion -> RemoteVerdict.FROM_NEWER
    remoteVersion == currentVersion -> RemoteVerdict.USABLE
    else -> RemoteVerdict.UNUSABLE
}

private sealed interface RemotePayload {
    data class Usable(val data: FavoriteSyncData) : RemotePayload
    data object Unusable : RemotePayload
    data object FromNewerVersion : RemotePayload
}

@Singleton
class WebDavSyncRepository @Inject constructor(
    private val webDavClient: WebDavClient,
    private val favoriteDao: FavoriteDao,
    private val favoriteFolderDao: FavoriteFolderDao,
    private val historyDao: HistoryDao,
    private val settingsRepository: SettingsRepository,
    /** 各源的内容备份实现，按源查表；没实现的源只同步元数据 */
    contentBackups: Set<@JvmSuppressWildcards SourceContentBackup>,
    @Named("main") private val mainClient: OkHttpClient,
    private val json: Json,
    @ApplicationContext private val appContext: Context,
    private val database: AppDatabase,
) {

    private val contentBackupBySource: Map<WorkSource, SourceContentBackup> =
        contentBackups.associateBy { it.source }

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    private val _testConnectionState = MutableStateFlow(TestConnectionState.IDLE)
    val testConnectionState: StateFlow<TestConnectionState> = _testConnectionState.asStateFlow()
    private val syncMutex = Mutex()

    /** 上次成功同步的 payload，用来判定本机删掉了什么。可能到 MB 级，所以落文件不落 prefs */
    private val snapshotStore = FavoriteSyncSnapshotStore(
        file = File(appContext.filesDir, SNAPSHOT_FILE_NAME),
        json = json,
    )

    /**
     * 完整同步：合并元数据 + 备份已浏览作品的内容。
     */
    suspend fun sync(): SyncResult = runSync(backupContent = true)

    /**
     * 仅同步元数据（不备份内容）。
     */
    suspend fun syncMetadataOnly(): SyncResult = runSync(backupContent = false)

    /**
     * 仅备份已浏览作品的内容到 WebDAV（不合并元数据）。
     */
    suspend fun backupContentOnly(): SyncResult = runSync(backupContent = true, skipMerge = true)

    private suspend fun runSync(
        backupContent: Boolean,
        skipMerge: Boolean = false,
    ): SyncResult = withContext(Dispatchers.IO) {
        val config = readConfig()
        Log.d(TAG, "runSync: backupContent=$backupContent skipMerge=$skipMerge config=$config")
        if (config is SyncConfig.Missing) {
            val result = SyncResult(SyncState.FAILED, error = config.reason)
            _syncState.value = SyncState.FAILED
            settingsRepository.recordSyncResult(result)
            return@withContext result
        }
        val (url, username, password) = config as SyncConfig.Ready
        val credentials = WebDavClient.basicAuth(username, password)

        _syncState.value = SyncState.SYNCING
        val result = try {
            if (!syncMutex.tryLock()) {
                Log.w(TAG, "runSync: mutex already held, skipping")
                SyncResult(SyncState.FAILED, error = "同步正在进行中")
            } else {
                try {
                    val finalResult = executeSync(url, credentials, backupContent, skipMerge)
                    finalResult
                } finally {
                    syncMutex.unlock()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: WebDavException) {
            Log.w(TAG, "sync webdav error: ${e.message}", e)
            SyncResult(SyncState.FAILED, error = e.message)
        } catch (e: Exception) {
            Log.e(TAG, "sync failed", e)
            SyncResult(SyncState.FAILED, error = e.message ?: "未知错误")
        }
        Log.d(TAG, "runSync: final state=${result.state} error=${result.error}")
        _syncState.value = if (result.state == SyncState.SUCCESS) SyncState.IDLE else SyncState.FAILED
        settingsRepository.recordSyncResult(result)
        result
    }

    private suspend fun executeSync(
        url: String,
        credentials: String,
        backupContent: Boolean,
        skipMerge: Boolean,
    ): SyncResult {
        Log.d(TAG, "executeSync: ensureDirectory piku/")
        webDavClient.ensureDirectory(url, "piku", credentials)
        val localFolders = favoriteFolderDao.observeFolders().first()
        val localFavorites = favoriteDao.observeAll().first()
        val allMemberships = readAllMemberships(localFolders)
        Log.d(TAG, "executeSync: local folders=${localFolders.size} favorites=${localFavorites.size} memberships=${allMemberships.size}")

        val merged = if (skipMerge) {
            buildLocalOnlySyncData(localFolders, localFavorites, allMemberships)
        } else {
            val remote = downloadRemoteData(url, credentials)
            if (remote is RemotePayload.FromNewerVersion) {
                Log.w(TAG, "executeSync: remote payload from a newer client, skipping sync")
                return SyncResult(
                    state = SyncState.FAILED,
                    error = "云端备份由更新版本的 Piku 写入，本次同步已跳过；升级后再试",
                )
            }
            val remoteData = (remote as? RemotePayload.Usable)?.data
            val snapshot = snapshotStore.read()
            Log.d(TAG, "executeSync: remoteData=${if (remoteData == null) "null" else "folders=${remoteData.folders.size} works=${remoteData.works.size}"} snapshot=${snapshot != null}")
            FavoriteSyncMerge.merge(
                localFolders = localFolders,
                localFavorites = localFavorites,
                localMemberships = allMemberships,
                remote = remoteData,
                snapshot = snapshot,
                now = System.currentTimeMillis(),
            )
        }

        Log.d(TAG, "executeSync: uploadMetadata folders=${merged.folders.size} works=${merged.works.size} memberships=${merged.memberships.size} tombstones=${merged.tombstones.size}")
        uploadMetadata(url, credentials, merged)
        if (!skipMerge) {
            writeLocalData(merged)
            // 本地写成功后再更新快照，否则会把还在本地的条目误判成已删除
            snapshotStore.write(merged)
        }

        val backedUp = if (backupContent) {
            backupViewedContent(url, credentials, merged)
        } else 0

        return SyncResult(
            state = SyncState.SUCCESS,
            backedUpWorks = backedUp,
        )
    }

    /**
     * 测试 WebDAV 连接：只 ping 一次 baseUrl，不创建任何目录。
     */
    suspend fun testConnection(): TestConnectionState = withContext(Dispatchers.IO) {
        _testConnectionState.value = TestConnectionState.TESTING
        val result = try {
            val config = readConfig()
            if (config is SyncConfig.Missing) {
                _testConnectionState.value = TestConnectionState.FAILED
                return@withContext TestConnectionState.FAILED
            }
            val (url, username, password) = config as SyncConfig.Ready
            val credentials = WebDavClient.basicAuth(username, password)
            webDavClient.ping(url, credentials)
            TestConnectionState.SUCCESS
        } catch (e: WebDavException) {
            Log.w(TAG, "testConnection webdav error", e)
            TestConnectionState.FAILED
        } catch (e: Exception) {
            Log.e(TAG, "testConnection failed", e)
            TestConnectionState.FAILED
        }
        _testConnectionState.value = result
        result
    }

    fun clearTestConnectionState() {
        _testConnectionState.value = TestConnectionState.IDLE
    }

    /**
     * 清掉残留的同步失败态。
     * syncState=FAILED 会一直停留到下一次同步，若上一次自动同步失败后一直没再同步，
     * 设置页会长期挂着"同步失败"，与刚刚成功的连接测试互相矛盾，因此测试成功时调用此方法复位。
     */
    fun clearSyncFailure() {
        if (_syncState.value == SyncState.FAILED) {
            _syncState.value = SyncState.IDLE
        }
    }

    // ──────────────────────── 内部方法 ────────────────────────

    private fun readConfig(): SyncConfig {
        val url = settingsRepository.webDavUrl.value
        val username = settingsRepository.webDavUsername.value
        val password = settingsRepository.webDavPassword.value
        return when {
            !settingsRepository.webDavEnabled.value -> SyncConfig.Missing("WebDAV 未启用")
            url.isBlank() -> SyncConfig.Missing("WebDAV 服务器地址未配置")
            username.isBlank() -> SyncConfig.Missing("WebDAV 用户名未配置")
            else -> SyncConfig.Ready(url, username, password)
        }
    }

    private suspend fun readAllMemberships(
        localFolders: List<FavoriteFolderEntity>,
    ): List<Pair<Long, FavoriteMembershipEntity>> {
        val allMemberships = favoriteFolderDao.observeAllMemberships().first()
        val folderIds = localFolders.map { it.id }.toSet()
        return allMemberships
            .filter { it.folderId in folderIds }
            .map { membership -> membership.folderId to membership }
    }

    private suspend fun downloadRemoteData(
        url: String,
        credentials: String,
    ): RemotePayload {
        return try {
            val bytes = webDavClient.downloadFile(url, "piku/favorites.json", credentials)
                ?: return RemotePayload.Unusable
            val parsed = json.decodeFromString<FavoriteSyncData>(String(bytes, Charsets.UTF_8))
            when (remoteVerdict(parsed.version, FavoriteSyncData.CURRENT_VERSION)) {
                RemoteVerdict.USABLE -> RemotePayload.Usable(parsed)
                RemoteVerdict.FROM_NEWER -> RemotePayload.FromNewerVersion
                RemoteVerdict.UNUSABLE -> {
                    Log.w(TAG, "remote favorites.json version ${parsed.version}, treating as first sync")
                    RemotePayload.Unusable
                }
            }
        } catch (e: WebDavException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "downloadRemoteData failed, treating as first sync", e)
            RemotePayload.Unusable
        }
    }

    /** skipMerge 专用：只打包本地内容，不带墓碑，不能用于常规同步 */
    private fun buildLocalOnlySyncData(
        localFolders: List<FavoriteFolderEntity>,
        localFavorites: List<FavoriteEntity>,
        localMemberships: List<Pair<Long, FavoriteMembershipEntity>>,
    ): FavoriteSyncData = FavoriteSyncData(
        version = FavoriteSyncData.CURRENT_VERSION,
        syncedAt = System.currentTimeMillis(),
        folders = localFolders.map { folder ->
            SyncFolder(
                id = folder.id,
                name = folder.name,
                isDefault = folder.isDefault,
                createdAt = folder.createdAt,
            )
        },
        works = localFavorites.map { it.toSyncWork() },
        memberships = localMemberships.map { (_, m) ->
            SyncMembership(folderId = m.folderId, source = m.source.name, workId = m.workId, addedAt = m.addedAt)
        }.distinctBy { Triple(it.folderId, it.source, it.workId) },
    )

    private suspend fun uploadMetadata(url: String, credentials: String, data: FavoriteSyncData) {
        val text = json.encodeToString(data)
        webDavClient.uploadFile(
            baseUrl = url,
            path = "piku/favorites.json",
            credentials = credentials,
            data = text.toByteArray(Charsets.UTF_8),
            contentType = "application/json; charset=utf-8",
        )
    }

    private suspend fun writeLocalData(data: FavoriteSyncData) {
        database.withTransaction {
            for (tombstone in data.tombstones) {
                val folder = favoriteFolderDao.folderByName(tombstone.folderName) ?: continue
                when (tombstone.kind) {
                    SyncTombstone.KIND_FOLDER -> {
                        if (folder.isDefault) continue
                        favoriteFolderDao.deleteFolder(folder.id)
                    }

                    SyncTombstone.KIND_MEMBERSHIP -> {
                        // 未知源本地永远没写过，无归属可删，跳过
                        val source = tombstone.workSource ?: continue
                        favoriteFolderDao.deleteMembership(folder.id, source, tombstone.workId)
                    }
                }
            }

            for (folder in data.folders) {
                val existing = favoriteFolderDao.folderByName(folder.name)
                if (existing == null) {
                    // id 交给本机分配：云端的 id 是别的设备上的自增值，照抄会撞本机同号的另一个夹
                    favoriteFolderDao.insertFolder(
                        FavoriteFolderEntity(
                            name = folder.name,
                            createdAt = folder.createdAt,
                            isDefault = folder.isDefault,
                        ),
                    )
                } else if (
                    existing.isDefault != folder.isDefault ||
                    existing.createdAt != folder.createdAt
                ) {
                    favoriteFolderDao.updateFolder(
                        existing.copy(
                            isDefault = folder.isDefault,
                            createdAt = folder.createdAt,
                        ),
                    )
                }
            }

            for (work in data.works) {
                // 未知源（更新版本客户端写入的）本地写不进去，云端保留原样
                val source = work.workSource ?: continue
                favoriteDao.upsert(
                    FavoriteEntity(
                        source = source,
                        workId = work.workId,
                        authorId = work.authorId,
                        title = work.title,
                        authorName = work.authorName,
                        thumbnailUrl = work.thumbnailUrl,
                        authorAvatarUrl = work.authorAvatarUrl,
                        imageCount = work.imageCount,
                        r18 = work.r18,
                        addedAt = work.addedAt,
                        contentBackedUp = work.contentBackedUp,
                    ),
                )
            }

            // 归属要落到本机自己的夹上：夹跨设备按名字对齐，云端的 folderId 会在外键上炸
            val localFolderIdByName = favoriteFolderDao.allFoldersOnce().associate { it.name to it.id }
            for (target in FavoriteSyncWritePlan.membershipTargets(data, localFolderIdByName)) {
                val source = target.membership.workSource ?: continue
                favoriteFolderDao.upsertMembership(
                    FavoriteMembershipEntity(
                        folderId = target.folderId,
                        source = source,
                        workId = target.membership.workId,
                        addedAt = target.membership.addedAt,
                    ),
                )
            }

            favoriteFolderDao.deleteOrphanedFavorites()
        }
    }

    /**
     * 备份已浏览作品的内容到 WebDAV。
     * 扁平化路径：piku/{夹}/image/{源}_{workId}_{序号}.{ext} + piku/{夹}/text/{源}_{workId}.txt
     * 多收藏夹作品按每个 (夹, 作品) 对都备份一份。
     *
     * 内容怎么取由各源的 [SourceContentBackup] 负责，没实现的源整源跳过；
     * 历史与归属都按 (source, workId) 配对，两源同号的作品不会互相顶掉。
     */
    private suspend fun backupViewedContent(
        url: String,
        credentials: String,
        data: FavoriteSyncData,
    ): Int {
        val historyKeys = historyDao.observeSince(0).first()
            .mapTo(mutableSetOf()) { WorkKey(it.source, it.workId) }
        // 本地 contentBackedUp = false 才需要尝试，避免对已经备份过的作品再发请求
        val localBackedUp = favoriteDao.observeAll().first()
            .associate { WorkKey(it.source, it.workId) to it.contentBackedUp }
        val workFolders = buildWorkFolderIndex(data)

        var backedUpCount = 0
        for (work in data.works) {
            val source = work.workSource ?: continue
            val backup = contentBackupBySource[source] ?: continue
            val key = WorkKey(source, work.workId)
            if (key !in historyKeys) continue
            if (localBackedUp[key] ?: work.contentBackedUp) continue
            val folders = workFolders[key].orEmpty()
            if (folders.isEmpty()) continue
            coroutineContext.ensureActive()

            try {
                val content = backup.content(
                    BackupWork(
                        workId = work.workId,
                        authorId = work.authorId,
                        imageCount = work.imageCount,
                    ),
                ) ?: continue

                var anyUploaded = false
                if (content.images.isNotEmpty() && backupImages(url, credentials, folders, key, content.images)) {
                    anyUploaded = true
                }
                if (content.novelText.isNotBlank() &&
                    backupNovelText(url, credentials, folders, key, content.novelText)
                ) {
                    anyUploaded = true
                }

                if (anyUploaded) {
                    favoriteDao.setContentBackedUp(source, work.workId, true)
                    backedUpCount++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: WebDavException) {
                Log.w(TAG, "backupViewedContent webdav error for work $key", e)
            } catch (e: Exception) {
                Log.w(TAG, "backupViewedContent: failed for work $key", e)
            }
        }

        return backedUpCount
    }

    /** (源, workId) → 该作品要写进哪些收藏夹目录 */
    private fun buildWorkFolderIndex(data: FavoriteSyncData): Map<WorkKey, List<String>> {
        val folderNamesById = data.folders.associate { it.id to it.name }
        return data.memberships
            .mapNotNull { membership ->
                val source = membership.workSource ?: return@mapNotNull null
                val folderName = folderNamesById[membership.folderId] ?: return@mapNotNull null
                WorkKey(source, membership.workId) to folderName
            }
            .groupBy({ it.first }, { it.second })
    }

    private suspend fun backupImages(
        baseUrl: String,
        credentials: String,
        folderNames: List<String>,
        key: WorkKey,
        imageUrls: List<String>,
    ): Boolean {
        var uploaded = false
        for ((index, imageUrl) in imageUrls.withIndex()) {
            val ext = imageExtension(imageUrl)
            for (folderName in folderNames) {
                val path = WebDavBackupPath.image(folderName, key, index, ext)
                if (webDavClient.exists(baseUrl, path, credentials)) continue
                if (downloadAndUpload(baseUrl, credentials, imageUrl, path)) {
                    uploaded = true
                }
            }
        }
        return uploaded
    }

    private suspend fun backupNovelText(
        baseUrl: String,
        credentials: String,
        folderNames: List<String>,
        key: WorkKey,
        text: String,
    ): Boolean {
        var uploaded = false
        for (folderName in folderNames) {
            val path = WebDavBackupPath.text(folderName, key)
            if (webDavClient.exists(baseUrl, path, credentials)) continue
            webDavClient.uploadFile(
                baseUrl = baseUrl,
                path = path,
                credentials = credentials,
                data = text.toByteArray(Charsets.UTF_8),
                contentType = "text/plain; charset=utf-8",
            )
            uploaded = true
        }
        return uploaded
    }

    private suspend fun downloadAndUpload(
        baseUrl: String,
        credentials: String,
        sourceUrl: String,
        destPath: String,
    ): Boolean {
        return try {
            val request = Request.Builder().url(sourceUrl).get().build()
            val response = mainClient.newCall(request).execute()
            response.use { r ->
                if (!r.isSuccessful) return@use false
                val contentType = r.body?.contentType()?.toString() ?: "application/octet-stream"
                val bytes = r.body?.bytes() ?: return@use false
                webDavClient.uploadFile(
                    baseUrl = baseUrl,
                    path = destPath,
                    credentials = credentials,
                    data = bytes,
                    contentType = contentType,
                )
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: WebDavException) {
            Log.w(TAG, "downloadAndUpload webdav error: $sourceUrl", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "downloadAndUpload failed: $sourceUrl", e)
            false
        }
    }

    companion object {
        private const val TAG = "WebDavSyncRepo"

        /** 同步快照文件名 */
        private const val SNAPSHOT_FILE_NAME = "favorite_sync_snapshot.json"
    }
}
