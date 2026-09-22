package com.piku.client.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Upsert
    suspend fun upsert(entity: FavoriteEntity)

    @Query("UPDATE favorites SET contentBackedUp = :backedUp WHERE workId = :workId")
    suspend fun setContentBackedUp(workId: String, backedUp: Boolean)

    @Query("DELETE FROM favorites WHERE workId = :workId")
    suspend fun delete(workId: String)

    @Query("SELECT COUNT(*) FROM favorites")
    fun observeCount(): Flow<Int>

    /**
     * 「全部收藏」视图的排序。作用在 favorites 表上，不需要 JOIN 归属表：
     * 作品行只有在失去全部归属时才会被删除，所以每行都至少属于一个收藏夹。
     * observeAll() 就是这里的「加入时间」顺序，不再重复定义。
     */
    @Query("SELECT * FROM favorites ORDER BY title COLLATE NOCASE ASC, addedAt DESC")
    fun observeAllByTitle(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY authorName COLLATE NOCASE ASC, addedAt DESC")
    fun observeAllByAuthor(): Flow<List<FavoriteEntity>>

    /** 这些作品的全部归属（取消收藏、收拢到单个夹时取快照） */
    @Query("SELECT * FROM favorite_memberships WHERE workId IN (:workIds)")
    suspend fun membershipsForWorks(workIds: List<String>): List<FavoriteMembershipEntity>

    /** 把作品从所有收藏夹移除（「全部收藏」视图里的取消收藏） */
    @Query("DELETE FROM favorite_memberships WHERE workId IN (:workIds)")
    suspend fun deleteMembershipsForWorks(workIds: List<String>)

    /** 批量取作品行：撤销移出时需要把被删掉的作品行原样写回 */
    @Query("SELECT * FROM favorites WHERE workId IN (:workIds)")
    suspend fun favoritesByIds(workIds: List<String>): List<FavoriteEntity>

    /** 批量清理失去全部归属的作品行（移出收藏夹后调用） */
    @Query(
        "DELETE FROM favorites WHERE workId IN (:workIds) " +
            "AND workId NOT IN (SELECT workId FROM favorite_memberships)",
    )
    suspend fun deleteOrphansByIds(workIds: List<String>)
}
