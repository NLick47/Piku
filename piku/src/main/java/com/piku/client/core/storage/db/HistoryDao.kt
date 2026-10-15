package com.piku.client.core.storage.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.piku.client.core.model.WorkSource
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE visitedAt >= :cutoff AND source IN (:sources) ORDER BY visitedAt DESC")
    fun observeSince(cutoff: Long, sources: List<WorkSource>): Flow<List<HistoryEntity>>

    @Upsert
    suspend fun upsert(entity: HistoryEntity)

    @Query("DELETE FROM history WHERE source IN (:sources)")
    suspend fun clear(sources: List<WorkSource>)

    @Query("DELETE FROM history WHERE source = :source AND workId = :workId")
    suspend fun deleteByWorkId(source: WorkSource, workId: String)

    @Query("DELETE FROM history WHERE visitedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}