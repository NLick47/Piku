package com.piku.client.data.repository

import com.piku.client.data.local.FavoriteDao
import com.piku.client.data.local.FavoriteFolderDao
import com.piku.client.data.local.FavoriteFolderEntity
import com.piku.client.data.local.FavoriteMembershipEntity
import com.piku.client.data.local.toFavoriteEntity
import com.piku.client.data.local.toWork
import com.piku.client.domain.model.FavoriteFolder
import com.piku.client.domain.model.Work
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepository @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val favoriteFolderDao: FavoriteFolderDao,
) {

    fun observeFavoriteIds(): Flow<Set<String>> =
        favoriteFolderDao.observeAllFavoriteIds().map { it.toSet() }

    fun observeFavorites(): Flow<List<Work>> =
        favoriteDao.observeAll().map { list -> list.map { it.toWork() } }

fun observeFolders(): Flow<List<FavoriteFolder>> = flow {
    // 首次订阅即保证默认收藏夹存在：直接进入收藏页时，快速收藏的落点也必须有
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

    /**
     * 快速收藏切换：把作品加入/移出默认收藏夹。
     *
     * @return true 表示本次操作后作品已收藏（加入默认夹），false 表示已取消收藏（移出默认夹）。
     */
    suspend fun toggleFavorite(work: Work): Boolean {
        val workId = work.id.toString()
        val folderIds = favoriteFolderDao.observeFolderIdsForWork(workId).first().toSet()
        val defaultFolderId = ensureDefaultFolder()
        return if (defaultFolderId in folderIds) {
            removeFromFolder(workId, defaultFolderId)
            false
        } else {
            addToFolder(work, defaultFolderId)
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
        return folderId
    }

    suspend fun renameFolder(folderId: Long, name: String) {
        favoriteFolderDao.renameFolder(folderId, name.trim().ifBlank { "未命名收藏夹" })
    }

    /**
     * 删除收藏夹。
     *
     * 默认收藏夹不可删除：它是快速收藏的落点，删除后快速收藏将失去目标，
     * 因此这里直接拒绝并返回 false。
     *
     * @return true 表示删除成功，false 表示拒绝删除（默认收藏夹）。
     */
    suspend fun deleteFolder(folderId: Long): Boolean {
        val defaultFolderId = favoriteFolderDao.defaultFolderId() ?: return true
        if (folderId == defaultFolderId) return false
        favoriteFolderDao.deleteFolder(folderId)
        favoriteFolderDao.deleteOrphanedFavorites()
        return true
    }

    private suspend fun addToFolder(work: Work, folderId: Long) {
        val workId = work.id.toString()
        favoriteDao.upsert(work.toFavoriteEntity())
        favoriteFolderDao.insertMembership(
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

    /**
     * 把作品从 [fromFolderId] 移动到 [toFolderId]。
     * 先加入目标再移出来源：目标与来源相同时保持原样，避免中间态丢失收藏记录。
     */
    suspend fun moveWork(work: Work, fromFolderId: Long, toFolderId: Long) {
        if (fromFolderId == toFolderId) return
        addToFolder(work, toFolderId)
        removeFromFolder(work.id.toString(), fromFolderId)
    }

    /** 确保默认收藏夹存在（快速收藏的落点，全 App 唯一）。返回其 id。 */
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
}
