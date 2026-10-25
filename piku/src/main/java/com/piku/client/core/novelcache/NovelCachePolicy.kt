package com.piku.client.core.novelcache

import com.piku.client.core.model.NovelChapter
import com.piku.client.core.reader.NovelChapterProgress
import java.security.MessageDigest

/**
 * 缓存策略的纯函数层：逐章合并、进度换算、淘汰目标。
 * 不碰数据库与 Android，单测直接喂输入断言输出。
 */
internal object NovelCachePolicy {

    const val FORMAT_VERSION = 1

    data class MergedChapter(
        val ordinal: Int,
        val chapter: NovelChapter,
        val contentHash: String,
        val charCount: Int,
        val byteSize: Long,
    )

    data class MergePlan(
        val upserts: List<MergedChapter>,
        val chapterCount: Int,
        val charCount: Int,
        val byteSize: Long,
        val oldLengths: List<Int>,
        val newLengths: List<Int>,
    )

    /** 只写 hash 变了的章与新增章；章数变少时由调用方截掉尾部 */
    fun mergePlan(existing: List<NovelCacheDao.ChapterDigest>, incoming: List<NovelChapter>): MergePlan {
        val upserts = ArrayList<MergedChapter>()
        var charCount = 0
        var byteSize = 0L
        incoming.forEachIndexed { index, chapter ->
            val hash = hash(chapter.title, chapter.text)
            val bytes = utf8Bytes(chapter.title) + utf8Bytes(chapter.text)
            charCount += chapter.text.length
            byteSize += bytes
            val old = existing.getOrNull(index)
            if (old == null || old.contentHash != hash) {
                upserts += MergedChapter(index, chapter, hash, chapter.text.length, bytes)
            }
        }
        return MergePlan(
            upserts = upserts,
            chapterCount = incoming.size,
            charCount = charCount,
            byteSize = byteSize,
            oldLengths = existing.map { it.charCount },
            newLengths = incoming.map { it.text.length },
        )
    }

    /**
     * 加更后把整部百分比换算到新总长：先按旧章长还原成字符偏移，再按新总长折回。
     * 不这么做的话，10 章读到 50% 会在涨到 12 章后跳到第 6 章。
     */
    fun rescalePercent(oldLengths: List<Int>, newLengths: List<Int>, percent: Int): Int {
        val clamped = percent.coerceIn(0, 100)
        if (clamped <= 0 || oldLengths.isEmpty() || newLengths.isEmpty()) return clamped
        val position = NovelChapterProgress.locate(oldLengths, clamped)
        val before = oldLengths.take(position.chapterIndex).sumOf { it.toLong() }
        val offset = before + oldLengths[position.chapterIndex].toLong() * position.localPercent / 100
        val total = newLengths.sumOf { it.toLong() }.coerceAtLeast(1)
        return (offset * 100 / total).toInt().coerceIn(0, 100)
    }

    fun hash(title: String, text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(title.toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(text.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun utf8Bytes(value: String): Long = value.toByteArray(Charsets.UTF_8).size.toLong()
}
