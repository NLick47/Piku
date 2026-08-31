package com.piku.client.data.repository

import android.util.Log
import com.piku.client.data.local.FavoriteDao
import com.piku.client.data.local.FavoriteFolderDao
import com.piku.client.data.local.FavoriteFolderEntity
import com.piku.client.data.local.FavoriteMembershipEntity
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.local.toFavoriteEntity
import com.piku.client.data.local.toWork
import com.piku.client.domain.model.FavoriteFolder
import com.piku.client.domain.model.Work
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FavoriteRepository @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val favoriteFolderDao: FavoriteFolderDao,
    private val settingsRepository: SettingsRepository,
    private val webDavSyncRepository: WebDavSyncRepository,
) {
    // SupervisorJob：单个同步失败不会拖垮后续；与 app 进程同生命周期。
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 自动同步触发源：用 SharedFlow 而不是直接 launch，可以让连续操作被 debounce + conflate 合并。
    private val autoSyncTrigger = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        scope.launch {
            autoSyncTrigger
                .onEach {
                    Log.d(TAG, "autoSync: trigger received, debouncing ${AUTO_SYNC_DEBOUNCE_MS}ms")
                    delay(AUTO_SYNC_DEBOUNCE_MS)
                }
                .collect {
                    Log.d(TAG, "autoSync: debounce fired, calling sync")
                    try {
                        val result = webDavSyncRepository.sync()
                        Log.d(TAG, "autoSync: result state=${result.state} error=${result.error}")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "autoSync failed", e)
                    }
                }
        }
    }

    fun observeFavoriteIds(): Flow<Set<Long>> =
        favoriteFolderDao.observeAllFavoriteIds().map { ids ->
            ids.mapNotNull { it.toLongOrNull() }.toSet()
        }

    fun observeFavorites(): Flow<List<Work>> =
        favoriteDao.observeAll().map { list -> list.map { it.toWork() } }

    fun observeFolders(): Flow<List<FavoriteFolder>> = flow {
        ensureDefaultFolder()
        emitAll(
            combine(
                favoriteFolderDao.observeFolders(),
                favoriteFolderDao.observeFolderCounts(),
                favoriteFolderDao.observeFolderPreviews(),
            ) { folders, counts, previews ->
                val countByFolder = counts.associate { it.folderId to it.count }
                val previewsByFolder = previews
                    .groupBy { it.folderId }
                    .mapValues { (_, list) -> list.take(3).map { it.thumbnailUrl } }
                folders.map { folder ->
                    FavoriteFolder(
                        id = folder.id,
                        name = folder.name,
                        workCount = countByFolder[folder.id] ?: 0,
                        previewUrls = previewsByFolder[folder.id] ?: emptyList(),
                        isDefault = folder.isDefault,
                    )
                }
            },
        )
    }

    fun observeFolderWorks(folderId: Long): Flow<List<Work>> =
        favoriteFolderDao.observeWorksInFolder(folderId).map { list -> list.map { it.toWork() } }

    fun observeWorkFolderIds(workId: Long): Flow<Set<Long>> =
        favoriteFolderDao.observeFolderIdsForWork(workId.toString()).map { it.toSet() }

    suspend fun toggleFavorite(work: Work): Boolean {
        val workId = work.id.toString()
        val folderIds = favoriteFolderDao.observeFolderIdsForWork(workId).first().toSet()
        val defaultFolderId = ensureDefaultFolder()
        return if (defaultFolderId in folderIds) {
            removeFromFolder(workId, defaultFolderId)
            triggerAutoSync()
            false
        } else {
            addToFolder(work, defaultFolderId)
            triggerAutoSync()
            true
        }
    }

    suspend fun toggleFolder(work: Work, folderId: Long) {
        val workId = work.id.toString()
        val folderIds = favoriteFolderDao.observeFolderIdsForWork(workId).first()
        if (folderId in folderIds) {
            removeFromFolder(workId, folderId)
        } else {
            addToFolder(work, folderId)
        }
        triggerAutoSync()
    }

    suspend fun createFolder(name: String, addCurrentWork: Work? = null): Long {
        val folderId = favoriteFolderDao.insertFolder(
            FavoriteFolderEntity(
                name = name.trim().ifBlank { "未命名收藏夹" },
                createdAt = System.currentTimeMillis(),
            ),
        )
        if (addCurrentWork != null) {
            addToFolder(addCurrentWork, folderId)
        }
        triggerAutoSync()
        return folderId
    }

    suspend fun renameFolder(folderId: Long, name: String) {
        favoriteFolderDao.renameFolder(folderId, name.trim().ifBlank { "未命名收藏夹" })
        triggerAutoSync()
    }

    suspend fun deleteFolder(folderId: Long): Boolean {
        val defaultFolderId = favoriteFolderDao.defaultFolderId() ?: return true
        if (folderId == defaultFolderId) return false
        favoriteFolderDao.deleteFolder(folderId)
        favoriteFolderDao.deleteOrphanedFavorites()
        triggerAutoSync()
        return true
    }

    private suspend fun addToFolder(work: Work, folderId: Long) {
        val workId = work.id.toString()
        favoriteDao.upsert(work.toFavoriteEntity())
        favoriteFolderDao.upsertMembership(
            FavoriteMembershipEntity(
                folderId = folderId,
                workId = workId,
                addedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun removeFromFolder(workId: String, folderId: Long) {
        favoriteFolderDao.deleteMembership(folderId, workId)
        val remaining = favoriteFolderDao.observeFolderIdsForWork(workId).first()
        if (remaining.isEmpty()) {
            favoriteDao.delete(workId)
        }
    }

    suspend fun moveWork(work: Work, fromFolderId: Long, toFolderId: Long) {
        if (fromFolderId == toFolderId) return
        addToFolder(work, toFolderId)
        removeFromFolder(work.id.toString(), fromFolderId)
        triggerAutoSync()
    }

    suspend fun ensureDefaultFolder(): Long {
        favoriteFolderDao.defaultFolderId()?.let { return it }
        return favoriteFolderDao.insertFolder(
            FavoriteFolderEntity(
                name = "默认收藏夹",
                createdAt = System.currentTimeMillis(),
                isDefault = true,
            ),
        )
    }

    /**
     * 触发 WebDAV 自动同步。
     * - 总开关未启用：直接 return
     * - 凭据不全：直接 return（UI 不应再静默吞错）
     * - 同一窗口内的多次触发会 debounce 合并为一次 syncMetadataOnly()
     */
    private fun triggerAutoSync() {
        val enabled = settingsRepository.webDavEnabled.value
        val url = settingsRepository.webDavUrl.value
        val username = settingsRepository.webDavUsername.value
        val passwordLen = settingsRepository.webDavPassword.value.length
        if (!enabled) {
            Log.d(TAG, "triggerAutoSync: skipped, webDavEnabled=false")
            return
        }
        if (url.isBlank() || username.isBlank()) {
            Log.d(TAG, "triggerAutoSync: skipped, url='$url' username='$username' urlBlank=${url.isBlank()} userBlank=${username.isBlank()}")
            return
        }
        val emitted = autoSyncTrigger.tryEmit(Unit)
        Log.d(TAG, "triggerAutoSync: tryEmit=$emitted url=$url username=$username passwordLen=$passwordLen")
    }

    companion object {
        private const val TAG = "FavoriteRepo"

        /** 自动同步去抖：合并 1.5 秒内的连续收藏/移动/重命名等操作。 */
        private const val AUTO_SYNC_DEBOUNCE_MS = 1_500L
    }
}
