package com.piku.client.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE workId = :workId)")
    fun isFavorite(workId: String): Flow<Boolean>

    @Upsert
    suspend fun upsert(entity: FavoriteEntity)

    @Query("UPDATE favorites SET contentBackedUp = :backedUp WHERE workId = :workId")
    suspend fun setContentBackedUp(workId: String, backedUp: Boolean)

    @Query("DELETE FROM favorites WHERE workId = :workId")
    suspend fun delete(workId: String)
}
