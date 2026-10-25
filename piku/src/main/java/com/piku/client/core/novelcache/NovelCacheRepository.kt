package com.piku.client.core.novelcache

import android.util.Log
import com.piku.client.core.model.AppError
import com.piku.client.core.model.NovelChapter
import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkKey
import com.piku.client.core.model.WorkSource
import com.piku.client.core.model.key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

fun interface NovelCacheClock {
    fun now(): Long
}

/** 阅读进度读写：仓储只在合并后做百分比校正时用 */
interface NovelProgressAccess {
    fun percent(key: WorkKey): Int

    fun setPercent(key: WorkKey, percent: Int)
}

/**
 * 正文缓存的唯一编排点：命中、取数、逐章合并、进度校正。
 * 各源只负责「怎么取到正文」，缓存策略不认站点名。
 */
@Singleton
class NovelCacheRepository @Inject constructor(
    private val store: NovelCacheStore,
    private val progress: NovelProgressAccess,
    private val clock: NovelCacheClock,
) {

    /** 同一个作品的并发取数只跑一次（详情预取与点阅读可能同时发生） */
    private val keyLocks = ConcurrentHashMap<WorkKey, Mutex>()

    fun observeSummaries(): Flow<List<NovelCacheSummary>> = store.observeAll().map { rows ->
        rows.mapNotNull { it.toSummary() }
    }

    fun observeTotalBytes(): Flow<Long> = store.observeTotalBytes()

    suspend fun snapshot(work: Work, remoteChapterCount: Int?): NovelCacheSnapshot? {
        val meta = store.meta(work.key) ?: return null
        if (meta.formatVersion != NovelCachePolicy.FORMAT_VERSION) return null
        val pending = ((remoteChapterCount ?: 0) - meta.chapterCount).coerceAtLeast(0)
        return NovelCacheSnapshot(
            chapterCount = meta.chapterCount,
            byteSize = meta.byteSize,
            fetchedAt = meta.fetchedAt,
            updateCount = pending,
        )
    }

    suspend fun cached(work: Work): CachedNovel? = cached(work.key)

    suspend fun cached(key: WorkKey): CachedNovel? = withContext(Dispatchers.IO) {
        readCached(key)
    }

    /**
     * 读正文：**有缓存就直接读缓存**，没有才走网络；读完不落盘。
     * 缓存只在用户点「缓存 / 更新」时写入，绝不因为打开阅读就悄悄存一份。
     *
     * [remoteChapterCount] 是详情页刚取回的章节数（AO3 才有）：它说明缓存已经落后时，
     * 这一读走网络拿最新，但仍然不写缓存——要不要更新由用户决定。
     */
    suspend fun read(
        work: Work,
        remoteChapterCount: Int?,
        fetch: suspend () -> Result<List<NovelChapter>>,
    ): Result<NovelRead> = withContext(Dispatchers.IO) {
        lockFor(work.key).withLock {
            val cached = readCached(work.key)
            val behind = remoteChapterCount != null &&
                remoteChapterCount > 0 &&
                cached != null &&
                remoteChapterCount != cached.chapters.size
            if (cached != null && !behind) {
                return@withLock Result.success(
                    NovelRead(chapters = cached.chapters, fromCache = true),
                )
            }
            fetch().fold(
                onSuccess = { chapters ->
                    if (chapters.isEmpty() || chapters.all { it.text.isBlank() }) {
                        Result.failure(AppError.NotFound)
                    } else {
                        Result.success(NovelRead(chapters = chapters, fromCache = false))
                    }
                },
                onFailure = { error ->
                    if (cached != null) {
                        Result.success(
                            NovelRead(
                                chapters = cached.chapters,
                                fromCache = true,
                                fallback = true,
                            ),
                        )
                    } else {
                        Result.failure(error)
                    }
                },
            )
        }
    }

    /** 用户显式点缓存/更新：取网并写入，失败不动已有缓存 */
    suspend fun download(
        work: Work,
        fetch: suspend () -> Result<List<NovelChapter>>,
    ): Result<List<NovelChapter>> = withContext(Dispatchers.IO) {
        lockFor(work.key).withLock {
            val cached = readCached(work.key)
            fetch().fold(
                onSuccess = { chapters ->
                    if (chapters.isEmpty() || chapters.all { it.text.isBlank() }) {
                        Result.failure(AppError.NotFound)
                    } else {
                        merge(work, cached, chapters, work.title, work.authorName)
                        Result.success(chapters)
                    }
                },
                onFailure = { Result.failure(it) },
            )
        }
    }

    /** poipiku 的正文随详情一起回来，由详情页在成功后调这里落缓存 */
    suspend fun save(
        work: Work,
        chapters: List<NovelChapter>,
        title: String = work.title,
        authorName: String = work.authorName,
    ): Boolean = withContext(Dispatchers.IO) {
        if (chapters.isEmpty() || chapters.all { it.text.isBlank() }) return@withContext false
        lockFor(work.key).withLock {
            val cached = readCached(work.key)
            merge(work, cached, chapters, title, authorName)
            true
        }
    }

    suspend fun delete(work: Work) = delete(work.key)

    suspend fun delete(key: WorkKey) = withContext(Dispatchers.IO) {
        store.delete(key)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        store.clearAll()
    }

    suspend fun totalBytes(): Long = withContext(Dispatchers.IO) { store.totalBytes() }

    private suspend fun merge(
        work: Work,
        cached: CachedNovel?,
        incoming: List<NovelChapter>,
        title: String,
        authorName: String,
    ) {
        val key = work.key
        val plan = NovelCachePolicy.mergePlan(store.digests(key), incoming)
        val now = clock.now()
        val meta = NovelCacheEntity(
            source = key.source.name,
            workId = key.workId,
            authorId = work.authorId,
            title = title.ifBlank { cached?.title.orEmpty() },
            authorName = authorName.ifBlank { cached?.authorName.orEmpty() },
            chapterCount = plan.chapterCount,
            charCount = plan.charCount,
            byteSize = plan.byteSize,
            fetchedAt = now,
            formatVersion = NovelCachePolicy.FORMAT_VERSION,
        )
        val upserts = plan.upserts.map {
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
        }
        store.write(meta, upserts, plan.chapterCount)
        if (cached == null) return
        val percent = progress.percent(key)
        if (percent <= 0) return
        val rescaled = NovelCachePolicy.rescalePercent(plan.oldLengths, plan.newLengths, percent)
        if (rescaled != percent) {
            Log.d(TAG, "cache merge rescale key=$key $percent% -> $rescaled%")
            progress.setPercent(key, rescaled)
        }
    }

    private suspend fun readCached(key: WorkKey): CachedNovel? {
        val meta = store.meta(key) ?: return null
        if (meta.formatVersion != NovelCachePolicy.FORMAT_VERSION) {
            store.delete(key)
            return null
        }
        val chapters = store.chapters(key)
        if (chapters.isEmpty()) {
            store.delete(key)
            return null
        }
        return CachedNovel(
            key = key,
            title = meta.title,
            authorName = meta.authorName,
            chapters = chapters.map { NovelChapter(title = it.title, text = it.text) },
            fetchedAt = meta.fetchedAt,
            byteSize = meta.byteSize,
        )
    }

    private fun lockFor(key: WorkKey): Mutex =
        keyLocks.computeIfAbsent(key) { Mutex() }

    private fun NovelCacheEntity.toSummary(): NovelCacheSummary? {
        val workSource = runCatching { WorkSource.valueOf(source) }.getOrNull() ?: return null
        return NovelCacheSummary(
            key = WorkKey(workSource, workId),
            title = title,
            authorName = authorName,
            authorId = authorId,
            chapterCount = chapterCount,
            byteSize = byteSize,
            fetchedAt = fetchedAt,
        )
    }

    private companion object {
        const val TAG = "PikuDiag"
    }
}
