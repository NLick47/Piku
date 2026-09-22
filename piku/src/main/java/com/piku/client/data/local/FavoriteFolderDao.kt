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

    /**
     * 收藏夹卡片的预览缩略图。空缩略图（小说等）在 SQL 里就滤掉——卡片上留个空框比少一张更难看。
     *
     * 「每夹取最近 3 张」留在仓库层 take(3)，不在这里用相关子查询数排名：
     * 那种写法要对每一行扫一遍本夹的归属，桌面 SQLite 实测（单夹 2000 件）
     * 从 1.9ms 涨到 250ms，代价远大于少传那 2000 行。
     */
    @Query(
        """
        SELECT m.folderId AS folderId, f.thumbnailUrl AS thumbnailUrl
        FROM favorite_memberships m
        INNER JOIN favorites f ON f.workId = m.workId
        WHERE f.thumbnailUrl != ''
        ORDER BY m.addedAt DESC
        """,
    )
    fun observeFolderPreviews(): Flow<List<FolderPreview>>

    /** 夹内列表默认顺序：加入时间倒序 */
    @Query("SELECT f.* FROM favorites f INNER JOIN favorite_memberships m ON f.workId = m.workId WHERE m.folderId = :folderId ORDER BY m.addedAt DESC")
    fun observeWorksInFolder(folderId: Long): Flow<List<FavoriteEntity>>

    /** 其余排序各自一条 SQL，而不是动态拼 ORDER BY 字符串：Room 在编译期校验语句。 */
    /** 按标题升序；同标题时用加入时间倒序兜底，保证顺序稳定不跳动 */
    @Query(
        "SELECT f.* FROM favorites f INNER JOIN favorite_memberships m ON f.workId = m.workId " +
            "WHERE m.folderId = :folderId ORDER BY f.title COLLATE NOCASE ASC, m.addedAt DESC",
    )
    fun observeWorksInFolderByTitle(folderId: Long): Flow<List<FavoriteEntity>>

    /** 按作者升序（作者名相同的作品自然相邻，可直接据此分组显示） */
    @Query(
        "SELECT f.* FROM favorites f INNER JOIN favorite_memberships m ON f.workId = m.workId " +
            "WHERE m.folderId = :folderId ORDER BY f.authorName COLLATE NOCASE ASC, m.addedAt DESC",
    )
    fun observeWorksInFolderByAuthor(folderId: Long): Flow<List<FavoriteEntity>>

    @Query("SELECT folderId FROM favorite_memberships WHERE workId = :workId")
    fun observeFolderIdsForWork(workId: String): Flow<List<Long>>

    /**
     * 同上的"取一次"版本：事务里做判定时不要订阅 Flow——
     * 事务内收集冷流会和失效通知纠缠，也不该为一次读建一条订阅。
     */
    @Query("SELECT folderId FROM favorite_memberships WHERE workId = :workId")
    suspend fun folderIdsForWork(workId: String): List<Long>

    @Query("SELECT DISTINCT workId FROM favorite_memberships")
    fun observeAllFavoriteIds(): Flow<List<String>>

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

    /** 取默认收藏夹：并发首次创建可能残留多个，固定取最早的一个，保证结果确定。 */
    @Query("SELECT id FROM favorite_folders WHERE isDefault = 1 ORDER BY id ASC LIMIT 1")
    suspend fun defaultFolderId(): Long?

    @Query("SELECT COUNT(*) FROM favorite_folders WHERE isDefault = 1")
    suspend fun defaultFolderCount(): Int

    /** 自愈：降级多余的默认收藏夹（早期版本并发创建可能留下不止一个）。 */
    @Query("UPDATE favorite_folders SET isDefault = 0 WHERE isDefault = 1 AND id != :keepId")
    suspend fun demoteOtherDefaults(keepId: Long)

    @Query("DELETE FROM favorites WHERE workId NOT IN (SELECT workId FROM favorite_memberships)")
    suspend fun deleteOrphanedFavorites()

    /**
     * 只属于该收藏夹的作品数：删除收藏夹时这些作品会失去唯一归属，被连带取消收藏。
     * 用于在删除确认框里说清后果。
     */
    @Query(
        """
        SELECT COUNT(*) FROM favorite_memberships
        WHERE folderId = :folderId
          AND workId NOT IN (SELECT workId FROM favorite_memberships WHERE folderId != :folderId)
        """,
    )
    suspend fun countExclusiveWorks(folderId: Long): Int

    /** 批量取归属快照（撤销时按原 addedAt 写回） */
    @Query("SELECT * FROM favorite_memberships WHERE folderId = :folderId AND workId IN (:workIds)")
    suspend fun membershipsIn(folderId: Long, workIds: List<String>): List<FavoriteMembershipEntity>

    /** 批量移出：一条 SQL 删除多条归属，避免逐个删除的 N 次写事务 */
    @Query("DELETE FROM favorite_memberships WHERE folderId = :folderId AND workId IN (:workIds)")
    suspend fun deleteMemberships(folderId: Long, workIds: List<String>)
}
