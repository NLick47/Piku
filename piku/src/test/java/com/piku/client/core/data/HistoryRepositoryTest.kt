package com.piku.client.core.data

import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkKind
import com.piku.client.core.model.WorkSource
import com.piku.client.core.storage.db.HistoryDao
import com.piku.client.core.storage.db.HistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryRepositoryTest {

    private class FakeHistoryDao : HistoryDao {
        /** 记录每次按作品删除时用的 (source, workId) */
        val deleted = mutableListOf<Pair<WorkSource, String>>()

        override fun observeSince(cutoff: Long, sources: List<WorkSource>): Flow<List<HistoryEntity>> =
            flowOf(emptyList())

        override suspend fun upsert(entity: HistoryEntity) = Unit

        override suspend fun clear(sources: List<WorkSource>) = Unit

        override suspend fun deleteByWorkId(source: WorkSource, workId: String) {
            deleted += source to workId
        }

        override suspend fun deleteOlderThan(cutoff: Long) = Unit
    }

    private val ao3Novel = Work(
        id = 83_451_331L,
        authorId = 7L,
        authorName = "作者",
        authorAvatarUrl = null,
        categoryCd = -1,
        categoryName = "",
        title = "作品",
        thumbnailUrl = "",
        imageCount = 0,
        r18 = false,
        source = WorkSource.AO3,
        kind = WorkKind.NOVEL,
    )

    /** 删除用的键要和 [com.piku.client.core.storage.db.toHistoryEntity] 写进去的键一致：小说是 n 前缀 */
    @Test
    fun removeTargetsTheSameKeyTheRowWasWrittenWith() = runBlocking {
        val dao = FakeHistoryDao()

        HistoryRepository(dao).remove(ao3Novel)

        assertEquals(listOf(WorkSource.AO3 to "n83451331"), dao.deleted)
    }
}
