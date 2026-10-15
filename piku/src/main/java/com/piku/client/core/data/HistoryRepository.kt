package com.piku.client.core.data
import com.piku.client.source.pixiv.data.repository.toWork

import com.piku.client.core.storage.db.HistoryDao
import com.piku.client.core.storage.db.HistoryEntity
import com.piku.client.core.storage.db.toHistoryEntity
import com.piku.client.core.storage.db.toWork
import com.piku.client.core.model.HistoryItem
import com.piku.client.core.model.HistoryTimeRange
import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkKey
import com.piku.client.core.model.WorkSource
import com.piku.client.core.model.key
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: HistoryDao,
) {

    /**
     * [sources] 是资料库池：只有池里的源会出现在列表里（默认全源）。
     * 池外的记录仍留在库里，切回那个源就看得到。
     */
    fun observeHistory(
        range: HistoryTimeRange = HistoryTimeRange.ALL,
        sources: List<WorkSource> = WorkSource.entries,
    ): Flow<List<HistoryItem>> =
        historyDao.observeSince(range.cutoffMillis(), sources).map { list ->
            list.map { HistoryItem(it.toWork(), it.visitedAt) }
        }

    suspend fun record(work: Work) = record(work, System.currentTimeMillis())

    suspend fun record(work: Work, visitedAt: Long) {
        historyDao.upsert(work.toHistoryEntity(visitedAt))
    }

    suspend fun remove(work: Work) {
        historyDao.deleteByWorkId(work.source, work.key.workId)
    }

    suspend fun remove(key: WorkKey) {
        historyDao.deleteByWorkId(key.source, key.workId)
    }

    /** 清空只作用于池内的源：在 AO3 侧点清空不该把图文侧的记录一起抹掉 */
    suspend fun clear(sources: List<WorkSource> = WorkSource.entries) {
        historyDao.clear(sources)
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
