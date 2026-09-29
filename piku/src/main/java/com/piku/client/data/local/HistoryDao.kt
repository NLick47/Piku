package com.piku.client.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.piku.client.domain.model.WorkSource
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE visitedAt >= :cutoff ORDER BY visitedAt DESC")
    fun observeSince(cutoff: Long): Flow<List<HistoryEntity>>

    @Upsert
    suspend fun upsert(entity: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clearAll()

    @Query("DELETE FROM history WHERE source = :source AND workId = :workId")
    suspend fun deleteByWorkId(source: WorkSource, workId: String)

    @Query("DELETE FROM history WHERE visitedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}