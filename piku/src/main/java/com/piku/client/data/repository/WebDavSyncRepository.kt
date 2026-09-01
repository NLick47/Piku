package com.piku.client.data.repository

import android.util.Log
import com.piku.client.data.local.FavoriteDao
import com.piku.client.data.local.FavoriteEntity
import com.piku.client.data.local.FavoriteFolderDao
import com.piku.client.data.local.FavoriteFolderEntity
import com.piku.client.data.local.FavoriteMembershipEntity
import com.piku.client.data.local.HistoryDao
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.local.toSyncWork
import com.piku.client.data.remote.PoipikuApi
import com.piku.client.data.remote.WebDavClient
import com.piku.client.data.remote.WebDavException
import com.piku.client.data.remote.WorkDetailParser
import com.piku.client.domain.model.FavoriteSyncData
import com.piku.client.domain.model.SyncFolder
import com.piku.client.domain.model.SyncMembership
import com.piku.client.domain.model.SyncWork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
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
 * 解析远端文件扩展名：URL path 末段的扩展名，默认为 .jpg。
 * WebDAV 上同一作品多张图共享 workId，所以不再区分 1.jpg / 2.jpg，
 * 按 hash 后缀 / 顺序号写到 piku/{folder}/image/{workId}_{index}.{ext}。
 */
private fun imageExtension(url: String, index: Int): String {
    val ext = url.substringAfterLast('.', missingDelimiterValue = "")
        .substringBefore('?')
        .lowercase()
    return if (ext.length in 1..5 && ext.all { it.isLetterOrDigit() }) ext else "jpg"
}

@Singleton
class WebDavSyncRepository @Inject constructor(
    private val webDavClient: WebDavClient,
    private val favoriteDao: FavoriteDao,
    private val favoriteFolderDao: FavoriteFolderDao,
    private val historyDao: HistoryDao,
    private val settingsRepository: SettingsRepository,
    private val poipikuApi: PoipikuApi,
    private val authRepository: AuthRepository,
    @Named("main") private val mainClient: OkHttpClient,
    private val json: Json,
) {

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    private val _testConnectionState = MutableStateFlow(TestConnectionState.IDLE)
    val testConnectionState: StateFlow<TestConnectionState> = _testConnectionState.asStateFlow()
    private val syncMutex = Mutex()

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
            val remoteData = downloadRemoteData(url, credentials)
            Log.d(TAG, "executeSync: remoteData=${if (remoteData == null) "null" else "folders=${remoteData.folders.size} works=${remoteData.works.size}"}")
            mergeData(localFolders, localFavorites, allMemberships, remoteData)
        }

        Log.d(TAG, "executeSync: uploadMetadata folders=${merged.folders.size} works=${merged.works.size} memberships=${merged.memberships.size}")
        uploadMetadata(url, credentials, merged)
        if (!skipMerge) {
            writeLocalData(merged)
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
    ): FavoriteSyncData? {
        return try {
            val bytes = webDavClient.downloadFile(url, "piku/favorites.json", credentials)
                ?: return null
            val text = String(bytes, Charsets.UTF_8)
            val parsed = json.decodeFromString<FavoriteSyncData>(text)
            if (parsed.version != CURRENT_VERSION) {
                Log.w(TAG, "remote favorites.json version mismatch: ${parsed.version}, treating as first sync")
                return null
            }
            parsed
        } catch (e: WebDavException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "downloadRemoteData failed, treating as first sync", e)
            null
        }
    }

    private fun buildLocalOnlySyncData(
        localFolders: List<FavoriteFolderEntity>,
        localFavorites: List<FavoriteEntity>,
        localMemberships: List<Pair<Long, FavoriteMembershipEntity>>,
    ): FavoriteSyncData = FavoriteSyncData(
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
            SyncMembership(folderId = m.folderId, workId = m.workId, addedAt = m.addedAt)
        }.distinctBy { it.folderId to it.workId },
    )

    /**
     * 合并本地和远程数据。
     */
    private fun mergeData(
        localFolders: List<FavoriteFolderEntity>,
        localFavorites: List<FavoriteEntity>,
        localMemberships: List<Pair<Long, FavoriteMembershipEntity>>,
        remote: FavoriteSyncData?,
    ): FavoriteSyncData {
        if (remote == null) {
            return buildLocalOnlySyncData(localFolders, localFavorites, localMemberships)
        }

        // 合并文件夹：按 name 匹配
        val remoteFolderByName = remote.folders.associateBy { it.name }
        val localFolderByName = localFolders.associateBy { it.name }
        val allFolderNames = localFolderByName.keys + remoteFolderByName.keys

        val mergedFolders = mutableListOf<SyncFolder>()
        for (name in allFolderNames) {
            val local = localFolderByName[name]
            val remoteFolder = remoteFolderByName[name]
            mergedFolders.add(
                SyncFolder(
                    id = remoteFolder?.id ?: local?.id ?: System.currentTimeMillis(),
                    name = name,
                    isDefault = (local?.isDefault == true) || (remoteFolder?.isDefault == true),
                    createdAt = minOf(
                        local?.createdAt ?: Long.MAX_VALUE,
                        remoteFolder?.createdAt ?: Long.MAX_VALUE,
                    ).let { if (it == Long.MAX_VALUE) System.currentTimeMillis() else it },
                ),
            )
        }

        // 合并作品：按 workId 去重
        val remoteWorkMap = remote.works.associateBy { it.workId }
        val localWorkMap = localFavorites.associateBy { it.workId }
        val allWorkIds = localWorkMap.keys + remoteWorkMap.keys

        val mergedWorks = allWorkIds.mapNotNull { workId ->
            val local = localWorkMap[workId]
            val remoteWork = remoteWorkMap[workId]
            if (local != null) {
                SyncWork(
                    workId = workId,
                    authorId = local.authorId,
                    title = local.title,
                    authorName = local.authorName,
                    thumbnailUrl = local.thumbnailUrl,
                    authorAvatarUrl = local.authorAvatarUrl,
                    imageCount = local.imageCount,
                    r18 = local.r18,
                    addedAt = maxOf(local.addedAt, remoteWork?.addedAt ?: 0),
                    contentBackedUp = local.contentBackedUp || (remoteWork?.contentBackedUp ?: false),
                )
            } else {
                remoteWork
            }
        }

        // 合并成员关系
        val remoteMembershipsByFolderName = remote.memberships.groupBy { m ->
            remote.folders.find { it.id == m.folderId }?.name ?: ""
        }
        val localMembershipsByFolderName = localMemberships.groupBy { (folderId, _) ->
            localFolders.find { it.id == folderId }?.name ?: ""
        }

        val mergedMemberships = mutableListOf<SyncMembership>()
        val allMembershipKeys = mutableSetOf<String>()

        for ((folderName, localMs) in localMembershipsByFolderName) {
            val mergedFolderId = mergedFolders.find { it.name == folderName }?.id ?: continue
            for ((_, m) in localMs) {
                val key = "$folderName:${m.workId}"
                if (allMembershipKeys.add(key)) {
                    mergedMemberships.add(
                        SyncMembership(folderId = mergedFolderId, workId = m.workId, addedAt = m.addedAt),
                    )
                }
            }
        }
        for ((folderName, remoteMs) in remoteMembershipsByFolderName) {
            val mergedFolderId = mergedFolders.find { it.name == folderName }?.id ?: continue
            for (m in remoteMs) {
                val key = "$folderName:${m.workId}"
                if (allMembershipKeys.add(key)) {
                    mergedMemberships.add(
                        SyncMembership(folderId = mergedFolderId, workId = m.workId, addedAt = m.addedAt),
                    )
                }
            }
        }

        return FavoriteSyncData(
            syncedAt = System.currentTimeMillis(),
            folders = mergedFolders,
            works = mergedWorks,
            memberships = mergedMemberships,
        )
    }

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
        val existingFolders = favoriteFolderDao.observeFolders().first()
        val existingByName = existingFolders.associateBy { it.name }

        for (folder in data.folders) {
            val existing = existingByName[folder.name]
            if (existing == null) {
                favoriteFolderDao.insertFolder(
                    FavoriteFolderEntity(
                        id = folder.id,
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
            favoriteDao.upsert(
                FavoriteEntity(
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

        for (membership in data.memberships) {
            favoriteFolderDao.upsertMembership(
                FavoriteMembershipEntity(
                    folderId = membership.folderId,
                    workId = membership.workId,
                    addedAt = membership.addedAt,
                ),
            )
        }

        favoriteFolderDao.deleteOrphanedFavorites()
    }

    /**
     * 备份已浏览作品的内容到 WebDAV。
     * 扁平化路径：piku/{folder}/image/{workId}_{index}.{ext} + piku/{folder}/text/{workId}.txt
     * 多文件夹作品按每个 (folder, work) 对都备份一份。
     */
    private suspend fun backupViewedContent(
        url: String,
        credentials: String,
        data: FavoriteSyncData,
    ): Int {
        val historyIds = historyDao.observeSince(0).first().map { it.workId }.toSet()
        // 本地 contentBackedUp = false 才需要尝试，避免对已经备份过的作品再发请求。
        val localBackedUp = favoriteDao.observeAll().first()
            .associate { it.workId to it.contentBackedUp }
        val worksToBackup = data.works.filter {
            it.workId in historyIds && !(localBackedUp[it.workId] ?: it.contentBackedUp)
        }
        if (worksToBackup.isEmpty()) return 0

        val workFolders = buildWorkFolderIndex(data)

        var backedUpCount = 0

        for ((index, work) in worksToBackup.withIndex()) {
            if (index > 0) politeFetchGap()
            coroutineContext.ensureActive()
            try {
                val workId = work.workId.toLongOrNull() ?: continue
                val folders = workFolders[work.workId].orEmpty()
                if (folders.isEmpty()) continue

                val workDetail = fetchWorkDetail(work.authorId, workId, work.imageCount) ?: continue
                var anyUploaded = false

                // 使用原图 URL，如果没有原图则降级使用缩略图
                val imageUrls = workDetail.fullImageUrls.ifEmpty { workDetail.detail.imageUrls }
                if (imageUrls.isNotEmpty()) {
                    if (backupImages(url, credentials, folders, workId, imageUrls)) {
                        anyUploaded = true
                    }
                }
                if (workDetail.detail.novelText.isNotBlank()) {
                    if (backupNovelText(url, credentials, folders, workId, workDetail.detail.novelText)) {
                        anyUploaded = true
                    }
                }

                if (anyUploaded) {
                    favoriteDao.setContentBackedUp(work.workId, true)
                    backedUpCount++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: WebDavException) {
                Log.w(TAG, "backupViewedContent webdav error for work ${work.workId}", e)
            } catch (e: Exception) {
                Log.w(TAG, "backupViewedContent: failed for work ${work.workId}", e)
            }
        }

        return backedUpCount
    }

    private fun buildWorkFolderIndex(data: FavoriteSyncData): Map<String, List<String>> {
        val folderNamesById = data.folders.associate { it.id to it.name }
        return data.memberships
            .groupBy { it.workId }
            .mapValues { (_, ms) ->
                ms.mapNotNull { folderNamesById[it.folderId] }
            }
    }

    private suspend fun politeFetchGap() {
        delay(FETCH_GAP_MIN_MS + fetchGapRandom.nextLong(FETCH_GAP_JITTER_MS))
    }

    private data class WorkDetailWithFullImages(
        val detail: com.piku.client.domain.model.WorkDetail,
        val fullImageUrls: List<String>,
    )

    private suspend fun fetchWorkDetail(
        authorId: Long,
        workId: Long,
        imageCount: Int,
    ): WorkDetailWithFullImages? {
        return try {
            val html = poipikuApi.getWorkDetail(authorId, workId).string()
            val detail = WorkDetailParser.parse(html)
            if (detail.passwordProtected && detail.imageUrls.isEmpty() && detail.novelText.isBlank()) {
                return null
            }

            // 单图直接获取原图，跳过 showAppendFile
            if (imageCount <= 1) {
                val fullImageUrls = fetchMainFullImage(authorId, workId)
                return WorkDetailWithFullImages(
                    detail = detail.copy(novelText = ""),
                    fullImageUrls = fullImageUrls,
                )
            }

            // 多图作品：调用 append API 获取所有图片（主图 + 追加图）
            delay(APPEND_FILE_GAP_MS)
            val appendResp = poipikuApi.showAppendFile(authorId, workId, "", 0, -1)
            val appendUrls = if (appendResp.result_num > 0) {
                WorkDetailParser.extractImageUrls(appendResp.html)
            } else emptyList()
            val novelText = WorkDetailParser.extractNovelText(appendResp.html)
            val mergedUrls = ThumbnailResolver.mergeWorkImages(detail.imageUrls, appendUrls)

            // 获取原图 URL
            val fullImageUrls = fetchFullImageUrls(authorId, workId, appendResp.html)

            WorkDetailWithFullImages(
                detail = detail.copy(imageUrls = mergedUrls, novelText = novelText),
                fullImageUrls = fullImageUrls,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "fetchWorkDetail failed for $authorId/$workId", e)
            null
        }
    }

    private suspend fun fetchMainFullImage(authorId: Long, workId: Long): List<String> {
        delay(ILLUST_DETAIL_GAP_MS)
        val resp = runCatching { poipikuApi.showIllustDetail(authorId, workId, -1, "") }.getOrNull()
        if (resp == null || resp.error_code != 0) {
            Log.d(TAG, "fetchMainFullImage failed: error_code=${resp?.error_code} work=$authorId/$workId")
            return emptyList()
        }
        return WorkDetailParser.extractFullImageUrls(resp.html)
    }

    private suspend fun fetchFullImageUrls(
        authorId: Long,
        workId: Long,
        appendHtml: String,
    ): List<String> {
        if (!authRepository.isLoggedIn()) return emptyList()

        val fullUrls = mutableListOf<String>()

        // 获取主图原图
        val mainUrls = fetchMainFullImage(authorId, workId)
        fullUrls.addAll(mainUrls)

        val ads = runCatching { WorkDetailParser.extractAppendAds(appendHtml) }.getOrDefault(emptyList())
        for (ad in ads) {
            delay(ILLUST_DETAIL_GAP_MS)
            val resp = runCatching { poipikuApi.showIllustDetail(authorId, workId, ad, "") }.getOrNull()
            if (resp != null && resp.error_code == 0) {
                val urls = WorkDetailParser.extractFullImageUrls(resp.html)
                urls.firstOrNull()?.let { fullUrls.add(it) }
            }
        }
        return fullUrls
    }

    private suspend fun backupImages(
        baseUrl: String,
        credentials: String,
        folderNames: List<String>,
        workId: Long,
        imageUrls: List<String>,
    ): Boolean {
        var uploaded = false
        for ((index, imageUrl) in imageUrls.withIndex()) {
            val ext = imageExtension(imageUrl, index)
            val fileName = "${workId}_${index}.$ext"
            for (folderName in folderNames) {
                val path = "piku/$folderName/image/$fileName"
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
        workId: Long,
        text: String,
    ): Boolean {
        var uploaded = false
        for (folderName in folderNames) {
            val path = "piku/$folderName/text/$workId.txt"
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

    private val fetchGapRandom = Random(System.nanoTime())

    companion object {
        private const val TAG = "WebDavSyncRepo"

        /** 抓取 poipiku 详情页之间的固定间隔基数 */
        private const val FETCH_GAP_MIN_MS = 1_200L

        /** 叠加的随机抖动上限，与基数合计 1.2~3.0 秒 */
        private const val FETCH_GAP_JITTER_MS = 1_800L

        /** 同域连续请求（详情页 → showAppendFile）之间的最小间隔 */
        private const val APPEND_FILE_GAP_MS = 800L

        /** showIllustDetail 请求之间的最小间隔 */
        private const val ILLUST_DETAIL_GAP_MS = 800L

        const val CURRENT_VERSION = 1
    }
}
