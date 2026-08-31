package com.piku.client.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class FolderCount(
    val folderId: Long,
    val count: Int,
)

/** 收藏夹内作品的缩略图预览（用于卡片直接展示内容）。 */
data class FolderPreview(
    val folderId: Long,
    val thumbnailUrl: String,
)

@Dao
interface FavoriteFolderDao {
    @Query("SELECT * FROM favorite_folders ORDER BY isDefault DESC, createdAt ASC")
    fun observeFolders(): Flow<List<FavoriteFolderEntity>>

    @Query("SELECT folderId, COUNT(*) AS count FROM favorite_memberships GROUP BY folderId")
    fun observeFolderCounts(): Flow<List<FolderCount>>

    @Query(
        """
        SELECT m.folderId AS folderId, f.thumbnailUrl AS thumbnailUrl
        FROM favorite_memberships m
        INNER JOIN favorites f ON f.workId = m.workId
        ORDER BY m.addedAt DESC
        """,
    )
    fun observeFolderPreviews(): Flow<List<FolderPreview>>

    @Query("SELECT f.* FROM favorites f INNER JOIN favorite_memberships m ON f.workId = m.workId WHERE m.folderId = :folderId ORDER BY m.addedAt DESC")
    fun observeWorksInFolder(folderId: Long): Flow<List<FavoriteEntity>>

    @Query("SELECT folderId FROM favorite_memberships WHERE workId = :workId")
    fun observeFolderIdsForWork(workId: String): Flow<List<Long>>

    @Query("SELECT DISTINCT workId FROM favorite_memberships")
    fun observeAllFavoriteIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_memberships WHERE workId = :workId)")
    fun isFavorite(workId: String): Flow<Boolean>

    @Insert
    suspend fun insertFolder(folder: FavoriteFolderEntity): Long

    @Update
    suspend fun updateFolder(folder: FavoriteFolderEntity)

    @Query("SELECT * FROM favorite_folders WHERE name = :name LIMIT 1")
    suspend fun folderByName(name: String): FavoriteFolderEntity?

    @Query("UPDATE favorite_folders SET name = :name WHERE id = :folderId")
    suspend fun renameFolder(folderId: Long, name: String)

    @Query("DELETE FROM favorite_folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: Long)

    @Upsert
    suspend fun upsertMembership(membership: FavoriteMembershipEntity)

    @Query("DELETE FROM favorite_memberships WHERE folderId = :folderId AND workId = :workId")
    suspend fun deleteMembership(folderId: Long, workId: String)

    @Query("SELECT * FROM favorite_memberships")
    fun observeAllMemberships(): Flow<List<FavoriteMembershipEntity>>

    @Query("SELECT id FROM favorite_folders WHERE isDefault = 1 LIMIT 1")
    suspend fun defaultFolderId(): Long?

    @Query("DELETE FROM favorites WHERE workId NOT IN (SELECT workId FROM favorite_memberships)")
    suspend fun deleteOrphanedFavorites()
}
