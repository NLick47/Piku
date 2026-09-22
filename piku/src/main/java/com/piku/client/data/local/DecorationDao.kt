package com.piku.client.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DecorationDao {

    /** widget 与管理页共用的唯一数据源，按加入时间倒序 */
    @Query("SELECT * FROM decoration_items ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<DecorationItem>>

    /** widget provideGlance 用的一次性快照 */
    @Query("SELECT * FROM decoration_items ORDER BY addedAt DESC")
    suspend fun getAllOnce(): List<DecorationItem>

    @Query("SELECT * FROM decoration_items WHERE workId = :workId")
    suspend fun get(workId: Long): DecorationItem?

    @Query("SELECT COUNT(*) FROM decoration_items")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(item: DecorationItem)

    @Query("DELETE FROM decoration_items WHERE workId = :workId")
    suspend fun delete(workId: Long)

    /** 总开关关闭并选择清除时整表清空 */
    @Query("DELETE FROM decoration_items")
    suspend fun deleteAll()
}
