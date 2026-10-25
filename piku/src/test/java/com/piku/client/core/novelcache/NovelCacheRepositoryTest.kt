package com.piku.client.core.novelcache

import com.piku.client.core.model.NovelChapter
import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkKey
import com.piku.client.core.model.WorkKind
import com.piku.client.core.model.WorkSource
import com.piku.client.core.model.key
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class NovelCacheRepositoryTest {

    private var now = 1_800_000_000_000L
    private var progressPercent = 0
    private val store = FakeStore()
    private val repository = NovelCacheRepository(
        store = store,
        progress = object : NovelProgressAccess {
            override fun percent(key: WorkKey): Int = progressPercent
            override fun setPercent(key: WorkKey, percent: Int) {
                progressPercent = percent
            }
        },
        clock = NovelCacheClock { now },
    )

    private fun work(
        source: WorkSource,
        id: Long,
        kind: WorkKind = WorkKind.NOVEL,
        inProgress: Boolean = false,
    ) = Work(
        id = id,
        authorId = 42,
        authorName = "作者",
        authorAvatarUrl = null,
        categoryCd = 0,
        categoryName = "",
        title = "作品 $id",
        thumbnailUrl = "",
        imageCount = 0,
        r18 = false,
        source = source,
        kind = kind,
        inProgress = inProgress,
    )

    private fun chapterList(count: Int, size: Int = 100): List<NovelChapter> =
        List(count) { NovelChapter(title = "第 ${it + 1} 章", text = "a".repeat(size)) }

    @Test
    fun cachedWorkReadsWithoutNetwork() = runBlocking {
        val work = work(WorkSource.AO3, 7)
        repository.download(work) { Result.success(chapterList(2)) }

        var fetched = false
        val result = repository.read(work, remoteChapterCount = 2) {
            fetched = true
            Result.failure(IllegalStateException("不该发请求"))
        }

        val read = result.getOrThrow()
        assertFalse("有缓存时不该取网", fetched)
        assertTrue(read.fromCache)
        assertEquals(2, read.chapters.size)
    }

    /** 用户没点缓存时，读一次不能顺手存一份 */
    @Test
    fun uncachedReadDoesNotWriteCache() = runBlocking {
        val work = work(WorkSource.PIXIV, 8)
        val result = repository.read(work, remoteChapterCount = null) {
            Result.success(chapterList(2))
        }

        assertEquals(2, result.getOrThrow().chapters.size)
        assertFalse(result.getOrThrow().fromCache)
        assertNull("读正文不应写缓存", store.meta(work.key))
    }

    @Test
    fun uncachedReadFailurePropagates() = runBlocking {
        val result = repository.read(work(WorkSource.PIXIV, 9), remoteChapterCount = null) {
            Result.failure(IOException("offline"))
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun downloadWritesCacheAndLaterReadsHitIt() = runBlocking {
        val work = work(WorkSource.AO3, 10)
        var fetched = 0
        repository.download(work) {
            fetched++
            Result.success(chapterList(2))
        }
        assertEquals(1, fetched)
        assertEquals(2, store.meta(work.key)?.chapterCount)

        val read = repository.read(work, remoteChapterCount = 2) {
            fetched++
            Result.failure(IllegalStateException("不该发请求"))
        }
        assertEquals(1, fetched)
        assertTrue(read.getOrThrow().fromCache)
    }

    @Test
    fun downloadOnlyWritesTheDelta() = runBlocking {
        val work = work(WorkSource.AO3, 11, inProgress = true)
        repository.download(work) { Result.success(chapterList(2)) }

        repository.download(work) { Result.success(chapterList(3)) }

        assertEquals(listOf(2), store.lastUpsertOrdinals)
        assertEquals(3, store.meta(work.key)?.chapterCount)
    }

    @Test
    fun downloadFailureKeepsExistingCache() = runBlocking {
        val work = work(WorkSource.AO3, 12)
        repository.download(work) { Result.success(chapterList(2)) }

        val result = repository.download(work) { Result.failure(IOException("offline")) }

        assertTrue(result.isFailure)
        assertEquals(2, store.meta(work.key)?.chapterCount)
        assertEquals(2, repository.cached(work)?.chapters?.size)
    }

    @Test
    fun appendingChaptersRescalesStoredProgress() = runBlocking {
        val work = work(WorkSource.AO3, 13, inProgress = true)
        repository.download(work) { Result.success(chapterList(10)) }
        progressPercent = 50

        repository.download(work) { Result.success(chapterList(12)) }

        assertEquals("50% 应折算到 12 章里的同一位置", 41, progressPercent)
        assertEquals(12, store.meta(work.key)?.chapterCount)
    }

    /** 同号作品跨源不能串：pixiv 小说 n123 与 poipiku 123 是两篇 */
    @Test
    fun sameIdDifferentSourcesStaySeparate() = runBlocking {
        val pixiv = work(WorkSource.PIXIV, 123)
        val poipiku = work(WorkSource.POIPIKU, 123, kind = WorkKind.ILLUST)
        repository.download(pixiv) { Result.success(listOf(NovelChapter("", "pixiv-text"))) }
        repository.download(poipiku) { Result.success(listOf(NovelChapter("", "poipiku-text"))) }

        assertEquals("pixiv-text", repository.cached(pixiv)?.chapters?.single()?.text)
        assertEquals("poipiku-text", repository.cached(poipiku)?.chapters?.single()?.text)
    }

    @Test
    fun mismatchedFormatVersionIsDiscardedOnRead() = runBlocking {
        val work = work(WorkSource.AO3, 31)
        store.seed(work, chapterList(1), NovelCachePolicy.FORMAT_VERSION + 1)

        var fetched = false
        val result = repository.read(work, remoteChapterCount = 1) {
            fetched = true
            Result.success(chapterList(2))
        }

        assertTrue(fetched)
        assertEquals(2, result.getOrThrow().chapters.size)
        assertNull("坏格式的缓存应被清掉", store.meta(work.key))
    }

    /** 缓存落后时这一读拿最新，但缓存本身不动：要不要更新是用户的事 */
    @Test
    fun readFetchesLatestWhenCacheIsBehindWithoutOverwritingIt() = runBlocking {
        val work = work(WorkSource.AO3, 14, inProgress = true)
        repository.download(work) { Result.success(chapterList(2)) }

        var fetched = false
        val result = repository.read(work, remoteChapterCount = 3) {
            fetched = true
            Result.success(chapterList(3))
        }

        assertTrue(fetched)
        assertEquals(3, result.getOrThrow().chapters.size)
        assertFalse(result.getOrThrow().fromCache)
        assertEquals("读正文不该改缓存", 2, store.meta(work.key)?.chapterCount)
    }

    @Test
    fun readFallsBackToCacheWhenBehindButNetworkFails() = runBlocking {
        val work = work(WorkSource.AO3, 15, inProgress = true)
        repository.download(work) { Result.success(chapterList(2)) }

        val result = repository.read(work, remoteChapterCount = 3) {
            Result.failure(IOException("offline"))
        }

        val read = result.getOrThrow()
        assertTrue(read.fromCache)
        assertTrue(read.fallback)
        assertEquals(2, read.chapters.size)
    }

    @Test
    fun snapshotReportsPendingChapters() = runBlocking {
        val work = work(WorkSource.AO3, 41, inProgress = true)
        repository.download(work) { Result.success(chapterList(2)) }

        val snapshot = repository.snapshot(work, remoteChapterCount = 4)

        assertNotNull(snapshot)
        assertEquals(2, snapshot!!.chapterCount)
        assertEquals(2, snapshot.updateCount)
    }

    private class FakeStore : NovelCacheStore {
        private val metas = LinkedHashMap<WorkKey, NovelCacheEntity>()
        private val chapters = mutableMapOf<WorkKey, MutableList<NovelChapterEntity>>()

        /** 最近一次写入里真正 upsert 的章序号，用来断言"只写增量" */
        var lastUpsertOrdinals: List<Int> = emptyList()
            private set

        override fun observeAll(): Flow<List<NovelCacheEntity>> =
            flowOf(metas.values.sortedByDescending { it.fetchedAt })

        override fun observeTotalBytes(): Flow<Long> = flowOf(metas.values.sumOf { it.byteSize })

        override suspend fun meta(key: WorkKey): NovelCacheEntity? = metas[key]

        override suspend fun chapters(key: WorkKey): List<NovelChapterEntity> =
            chapters[key].orEmpty().sortedBy { it.ordinal }

        override suspend fun digests(key: WorkKey): List<NovelCacheDao.ChapterDigest> =
            chapters(key).map {
                NovelCacheDao.ChapterDigest(it.ordinal, it.contentHash, it.charCount)
            }

        override suspend fun write(
            meta: NovelCacheEntity,
            upserts: List<NovelChapterEntity>,
            chapterCount: Int,
        ) {
            val key = WorkKey(WorkSource.valueOf(meta.source), meta.workId)
            metas[key] = meta
            val rows = chapters.getOrPut(key) { mutableListOf() }
            rows.removeAll { it.ordinal >= chapterCount }
            upserts.forEach { entity ->
                rows.removeAll { it.ordinal == entity.ordinal }
                rows += entity
            }
            lastUpsertOrdinals = upserts.map { it.ordinal }
        }

        override suspend fun delete(key: WorkKey) {
            metas.remove(key)
            chapters.remove(key)
        }

        override suspend fun clearAll() {
            metas.clear()
            chapters.clear()
        }

        override suspend fun totalBytes(): Long = metas.values.sumOf { it.byteSize }

        fun seed(work: Work, list: List<NovelChapter>, formatVersion: Int) {
            val key = work.key
            val plan = NovelCachePolicy.mergePlan(emptyList(), list)
            metas[key] = NovelCacheEntity(
                source = key.source.name,
                workId = key.workId,
                authorId = work.authorId,
                title = work.title,
                authorName = work.authorName,
                chapterCount = plan.chapterCount,
                charCount = plan.charCount,
                byteSize = plan.byteSize,
                fetchedAt = 0,
                formatVersion = formatVersion,
            )
            chapters[key] = plan.upserts.map {
                NovelChapterEntity(
                    source = key.source.name,
                    workId = key.workId,
                    ordinal = it.ordinal,
                    title = it.chapter.title,
                    text = it.chapter.text,
                    contentHash = it.contentHash,
                    charCount = it.charCount,
                    byteSize = it.byteSize,
                )
            }.toMutableList()
        }
    }
}
