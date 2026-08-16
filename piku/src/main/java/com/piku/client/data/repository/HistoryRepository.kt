package com.piku.client.data.repository

import com.piku.client.data.local.HistoryDao
import com.piku.client.data.local.HistoryEntity
import com.piku.client.data.local.toWork
import com.piku.client.domain.model.HistoryTimeRange
import com.piku.client.domain.model.Work
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: HistoryDao,
) {

    fun observeHistory(range: HistoryTimeRange = HistoryTimeRange.ALL): Flow<List<Work>> =
        historyDao.observeSince(range.cutoffMillis()).map { list -> list.map { it.toWork() } }

    suspend fun record(work: Work) {
        historyDao.upsert(
            HistoryEntity(
                workId = work.id.toString(),
                authorId = work.authorId,
                title = work.title,
                authorName = work.authorName,
                authorAvatarUrl = work.authorAvatarUrl,
                thumbnailUrl = work.thumbnailUrl,
                imageCount = work.imageCount,
                r18 = work.r18,
                visitedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clear() {
        historyDao.clearAll()
    }

    /** 清理超过 [days] 天的记录；days <= 0 表示永久保留，不执行清理 */
    suspend fun pruneOlderThan(days: Int) {
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * DAY_MILLIS
        historyDao.deleteOlderThan(cutoff)
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}