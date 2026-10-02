package com.piku.client.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.piku.client.domain.model.WorkSource
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Upsert
    suspend fun upsert(entity: FavoriteEntity)

    @Query("UPDATE favorites SET contentBackedUp = :backedUp WHERE source = :source AND workId = :workId")
    suspend fun setContentBackedUp(source: WorkSource, workId: String, backedUp: Boolean)

    @Query("DELETE FROM favorites WHERE source = :source AND workId = :workId")
    suspend fun delete(source: WorkSource, workId: String)

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

    /** 单源一批作品的全部归属（取消收藏、收拢到单个夹时取快照）；跨源由仓库层按源分组后拼调 */
    @Query("SELECT * FROM favorite_memberships WHERE source = :source AND workId IN (:workIds)")
    suspend fun membershipsForWorks(source: WorkSource, workIds: List<String>): List<FavoriteMembershipEntity>

    /** 单源一批作品的归属整体移除（「全部收藏」里的取消收藏）；跨源同上 */
    @Query("DELETE FROM favorite_memberships WHERE source = :source AND workId IN (:workIds)")
    suspend fun deleteMembershipsForWorks(source: WorkSource, workIds: List<String>)

    /** 批量取作品行：撤销移出时需要把被删掉的作品行原样写回 */
    @Query("SELECT * FROM favorites WHERE source = :source AND workId IN (:workIds)")
    suspend fun favoritesByIds(source: WorkSource, workIds: List<String>): List<FavoriteEntity>

    /** 单个作品行；addToFolder 追加归属时要带着原 cloudSynced，整行 upsert 会把它抹掉 */
    @Query("SELECT * FROM favorites WHERE source = :source AND workId = :workId LIMIT 1")
    suspend fun favoriteById(source: WorkSource, workId: String): FavoriteEntity?

    /** 云端镜像标记：pixiv 收藏的取消规则与批量同步都以它为准 */
    @Query("UPDATE favorites SET cloudSynced = :synced WHERE source = :source AND workId = :workId")
    suspend fun setCloudSynced(source: WorkSource, workId: String, synced: Boolean)

    /** 已镜像到 pixiv 云端的收藏键集合；详情页角标与收藏页批量同步共用 */
    @Query("SELECT source, workId FROM favorites WHERE cloudSynced = 1")
    fun observeSyncedFavoriteIds(): Flow<List<FavoriteIdRow>>

    /** 批量清理失去全部归属的作品行（移出收藏夹后调用） */
    @Query(
        "DELETE FROM favorites WHERE source = :source AND workId IN (:workIds) " +
            "AND NOT EXISTS (" +
            "SELECT 1 FROM favorite_memberships m " +
            "WHERE m.source = favorites.source AND m.workId = favorites.workId)",
    )
    suspend fun deleteOrphansByIds(source: WorkSource, workIds: List<String>)
}
