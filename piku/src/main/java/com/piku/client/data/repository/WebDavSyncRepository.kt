package com.piku.client.data.repository

import android.util.Log
import com.piku.client.data.local.FavoriteDao
import com.piku.client.data.local.FavoriteEntity
import com.piku.client.data.local.FavoriteFolderDao
import com.piku.client.data.local.FavoriteFolderEntity
import com.piku.client.data.local.FavoriteMembershipEntity
import com.piku.client.data.local.HistoryDao
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.PoipikuApi
import com.piku.client.data.remote.WebDavClient
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.coroutines.coroutineContext
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
    val newFolders: Int = 0,
    val newWorks: Int = 0,
    val backedUpWorks: Int = 0,
    val error: String? = null,
)

@Singleton
class WebDavSyncRepository @Inject constructor(
    private val webDavClient: WebDavClient,
    private val favoriteDao: FavoriteDao,
    private val favoriteFolderDao: FavoriteFolderDao,
    private val historyDao: HistoryDao,
    private val settingsRepository: SettingsRepository,
    private val poipikuApi: PoipikuApi,
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
    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        runSync { credentials, url ->
            val localFolders = favoriteFolderDao.observeFolders().first()
            val localFavorites = favoriteDao.observeAll().first()
            val allMemberships = readAllMemberships(localFolders)
            val remoteData = downloadRemoteData(url, credentials)
            val merged = mergeData(localFolders, localFavorites, allMemberships, remoteData)
            uploadMetadata(url, credentials, merged)
            writeLocalData(merged)
            val backedUp = backupViewedContent(url, credentials, merged)
            SyncResult(
                state = SyncState.SUCCESS,
                newFolders = merged.folders.size - (remoteData?.folders?.size ?: 0),
                newWorks = merged.works.size - (remoteData?.works?.size ?: 0),
                backedUpWorks = backedUp,
            )
        }
    }

    /**
     * 仅同步元数据（不备份内容）。
     */
    suspend fun syncMetadataOnly(): SyncResult = withContext(Dispatchers.IO) {
        runSync { credentials, url ->
            val localFolders = favoriteFolderDao.observeFolders().first()
            val localFavorites = favoriteDao.observeAll().first()
            val allMemberships = readAllMemberships(localFolders)
            val remoteData = downloadRemoteData(url, credentials)
            val merged = mergeData(localFolders, localFavorites, allMemberships, remoteData)
            uploadMetadata(url, credentials, merged)
            writeLocalData(merged)
            SyncResult(
                state = SyncState.SUCCESS,
                newFolders = merged.folders.size - (remoteData?.folders?.size ?: 0),
                newWorks = merged.works.size - (remoteData?.works?.size ?: 0),
            )
        }
    }

    /**
     * 测试 WebDAV 连接（独立于同步流程）。
     */
    suspend fun testConnection(): TestConnectionState = withContext(Dispatchers.IO) {
        _testConnectionState.value = TestConnectionState.TESTING
        try {
            val url = settingsRepository.webDavUrl.value
            val username = settingsRepository.webDavUsername.value
            val password = settingsRepository.webDavPassword.value
            if (url.isBlank() || username.isBlank()) {
                _testConnectionState.value = TestConnectionState.FAILED
                return@withContext TestConnectionState.FAILED
            }
            val credentials = WebDavClient.basicAuth(username, password)
            webDavClient.ensureDirectory(url, "piku", credentials)
            _testConnectionState.value = TestConnectionState.SUCCESS
            TestConnectionState.SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "testConnection failed", e)
            _testConnectionState.value = TestConnectionState.FAILED
            TestConnectionState.FAILED
        }
    }

    fun clearTestConnectionState() {
        _testConnectionState.value = TestConnectionState.IDLE
    }

    /**
     * 仅备份已浏览作品的内容到 WebDAV（不合并元数据）。
     */
    suspend fun backupContentOnly(): SyncResult = withContext(Dispatchers.IO) {
        runSync { credentials, url ->
            val localFolders = favoriteFolderDao.observeFolders().first()
            val localFavorites = favoriteDao.observeAll().first()
            val allMemberships = readAllMemberships(localFolders)
            val merged = mergeData(localFolders, localFavorites, allMemberships, null)
            val backedUp = backupViewedContent(url, credentials, merged)
            SyncResult(
                state = SyncState.SUCCESS,
                backedUpWorks = backedUp,
            )
        }
    }

    private suspend fun runSync(block: suspend (credentials: String, url: String) -> SyncResult): SyncResult {
        if (!syncMutex.tryLock()) {
            return SyncResult(SyncState.FAILED, error = "同步正在进行中")
        }
        return try {
            val url = settingsRepository.webDavUrl.value
            val username = settingsRepository.webDavUsername.value
            val password = settingsRepository.webDavPassword.value

            if (url.isBlank() || username.isBlank()) {
                return SyncResult(SyncState.FAILED, error = "WebDAV 未配置")
            }

            _syncState.value = SyncState.SYNCING

            val credentials = WebDavClient.basicAuth(username, password)
            webDavClient.ensureDirectory(url, "piku", credentials)
            val result = block(credentials, url)
            settingsRepository.recordSync(result)
            _syncState.value = SyncState.IDLE
            result
        } catch (e: Exception) {
            Log.e(TAG, "sync failed", e)
            val result = SyncResult(SyncState.FAILED, error = e.message)
            settingsRepository.recordSync(result)
            _syncState.value = SyncState.IDLE
            result
        } finally {
            syncMutex.unlock()
        }
    }

    // ──────────────────────── 内部方法 ────────────────────────

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
            val text = bytes.toString(Charsets.UTF_8)
            json.decodeFromString<FavoriteSyncData>(text)
        } catch (e: Exception) {
            Log.w(TAG, "downloadRemoteData failed, treating as first sync", e)
            null
        }
    }

    /**
     * 合并本地和远程数据。
     * - 文件夹：按 name 匹配合并
     * - 作品：按 workId 去重，取 addedAt 最大的版本
     * - 成员关系：按 (folderName, workId) 去重
     */
    private fun mergeData(
        localFolders: List<FavoriteFolderEntity>,
        localFavorites: List<FavoriteEntity>,
        localMemberships: List<Pair<Long, FavoriteMembershipEntity>>,
        remote: FavoriteSyncData?,
    ): FavoriteSyncData {
        if (remote == null) {
            return FavoriteSyncData(
                syncedAt = System.currentTimeMillis(),
                folders = localFolders.map { folder ->
                    SyncFolder(
                        id = folder.id,
                        name = folder.name,
                        isDefault = folder.isDefault,
                        createdAt = folder.createdAt,
                    )
                },
                works = localFavorites.map { fav ->
                    SyncWork(
                        workId = fav.workId,
                        authorId = fav.authorId,
                        title = fav.title,
                        authorName = fav.authorName,
                        thumbnailUrl = fav.thumbnailUrl,
                        authorAvatarUrl = fav.authorAvatarUrl,
                        imageCount = fav.imageCount,
                        r18 = fav.r18,
                        addedAt = fav.addedAt,
                    )
                },
                memberships = localMemberships.map { (_, m) ->
                    SyncMembership(
                        folderId = m.folderId,
                        workId = m.workId,
                        addedAt = m.addedAt,
                    )
                }.distinctBy { it.folderId to it.workId },
            )
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
                    contentBackedUp = remoteWork?.contentBackedUp ?: false,
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
                ),
            )
        }

        for (membership in data.memberships) {
            favoriteFolderDao.insertMembership(
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
     */
    private suspend fun backupViewedContent(
        url: String,
        credentials: String,
        data: FavoriteSyncData,
    ): Int {
        val historyIds = historyDao.observeSince(0).first().map { it.workId }.toSet()

        val toBackup = data.works.filter { work ->
            work.workId in historyIds && !work.contentBackedUp
        }

        if (toBackup.isEmpty()) return 0

        var backedUpCount = 0

        for ((index, work) in toBackup.withIndex()) {
            // 只在「作品之间」等待，不在每个 HTTP 请求前等待：
            // 单个作品有 1 次详情页请求 + N 次图片下载 + N 次 WebDAV exists + N 次 PUT，
            // 逐请求加延迟会让单个作品耗时膨胀到分钟级。
            if (index > 0) politeFetchGap()
            coroutineContext.ensureActive()
            try {
                val workId = work.workId.toLongOrNull() ?: continue
                val authorId = work.authorId

                val detail = fetchWorkDetail(authorId, workId) ?: continue
                val folderName = findFolderNameForWork(data, work) ?: "默认收藏夹"

                if (detail.imageUrls.isNotEmpty()) {
                    backupImages(url, credentials, folderName, workId, detail.imageUrls)
                }

                if (detail.novelText.isNotBlank()) {
                    backupNovelText(url, credentials, folderName, workId, detail.novelText)
                }

                backedUpCount++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "backupViewedContent: failed for work ${work.workId}", e)
            }
        }

        return backedUpCount
    }

    /**
     * 抓取详情页之间的礼貌间隔：固定基数 + 随机抖动，合计 1.2~3.0 秒。
     * 目的是避免对 poipiku 产生突发请求触发风控；WebDAV 侧是用户自己的服务器，不需要延迟。
     */
    private suspend fun politeFetchGap() {
        delay(FETCH_GAP_MIN_MS + fetchGapRandom.nextLong(FETCH_GAP_JITTER_MS))
    }

    private suspend fun fetchWorkDetail(
        authorId: Long,
        workId: Long,
    ): com.piku.client.domain.model.WorkDetail? {
        return try {
            val html = poipikuApi.getWorkDetail(authorId, workId).string()
            val detail = WorkDetailParser.parse(html)
            if (detail.passwordProtected && detail.imageUrls.isEmpty() && detail.novelText.isBlank()) {
                return null
            }
            if (detail.warning) {
                // 与上一次详情页请求同域且紧接着发出；RetryInterceptor 只重试 GET，
                // 这个 POST 没有退避保护，先自己隔开一点
                delay(APPEND_FILE_GAP_MS)
                val appendResp = poipikuApi.showAppendFile(authorId, workId, "", 0, -1)
                val appendUrls = if (appendResp.result_num > 0) {
                    WorkDetailParser.extractImageUrls(appendResp.html)
                } else emptyList()
                val novelText = WorkDetailParser.extractNovelText(appendResp.html)
                val mergedUrls = ThumbnailResolver.mergeWorkImages(detail.imageUrls, appendUrls)
                detail.copy(imageUrls = mergedUrls, novelText = novelText)
            } else {
                detail
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchWorkDetail failed for $authorId/$workId", e)
            null
        }
    }

    private fun findFolderNameForWork(data: FavoriteSyncData, work: SyncWork): String? {
        val membership = data.memberships.find { it.workId == work.workId } ?: return null
        return data.folders.find { it.id == membership.folderId }?.name
    }

    private suspend fun backupImages(
        baseUrl: String,
        credentials: String,
        folderName: String,
        workId: Long,
        imageUrls: List<String>,
    ) {
        val workDir = "piku/$folderName/$workId"
        for ((index, imageUrl) in imageUrls.withIndex()) {
            val fileName = "${index + 1}.jpg"
            val path = "$workDir/$fileName"
            if (webDavClient.exists(baseUrl, path, credentials)) continue
            downloadAndUpload(baseUrl, credentials, imageUrl, path)
        }
    }

    private suspend fun backupNovelText(
        baseUrl: String,
        credentials: String,
        folderName: String,
        workId: Long,
        text: String,
    ) {
        val path = "piku/$folderName/$workId/novel.txt"
        if (webDavClient.exists(baseUrl, path, credentials)) return
        webDavClient.uploadFile(
            baseUrl = baseUrl,
            path = path,
            credentials = credentials,
            data = text.toByteArray(Charsets.UTF_8),
            contentType = "text/plain; charset=utf-8",
        )
    }

    private suspend fun downloadAndUpload(
        baseUrl: String,
        credentials: String,
        sourceUrl: String,
        destPath: String,
    ) {
        try {
            val request = Request.Builder().url(sourceUrl).get().build()
            val response = mainClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return
            }
            val contentType = response.body?.contentType()?.toString() ?: "application/octet-stream"
            val bytes = response.body?.bytes()
            response.close()
            if (bytes != null) {
                webDavClient.uploadFile(
                    baseUrl = baseUrl,
                    path = destPath,
                    credentials = credentials,
                    data = bytes,
                    contentType = contentType,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "downloadAndUpload failed: $sourceUrl", e)
        }
    }

    private val fetchGapRandom = Random(System.currentTimeMillis())

    companion object {
        private const val TAG = "WebDavSyncRepo"

        /** 抓取 poipiku 详情页之间的固定间隔基数 */
        private const val FETCH_GAP_MIN_MS = 1_200L

        /** 叠加的随机抖动上限，与基数合计 1.2~3.0 秒 */
        private const val FETCH_GAP_JITTER_MS = 1_800L

        /** 同域连续请求（详情页 → showAppendFile）之间的最小间隔 */
        private const val APPEND_FILE_GAP_MS = 800L
    }
}
