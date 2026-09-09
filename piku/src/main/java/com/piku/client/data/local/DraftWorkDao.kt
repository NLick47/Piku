package com.piku.client.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DraftWorkDao {

    @Query("SELECT * FROM draft_works ORDER BY updatedAt DESC")
    suspend fun all(): List<DraftWorkEntity>

    @Query("SELECT * FROM draft_works WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): DraftWorkEntity?

    /** upsert：id 由客户端生成（时间戳），保证"继续编辑同一份草稿"走覆盖更新 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DraftWorkEntity)

    @Query("DELETE FROM draft_works WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM draft_works")
    suspend fun clearAll()
}
