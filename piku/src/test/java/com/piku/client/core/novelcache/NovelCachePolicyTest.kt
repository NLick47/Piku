package com.piku.client.core.novelcache

import com.piku.client.core.model.NovelChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelCachePolicyTest {

    private fun chapter(title: String, text: String) = NovelChapter(title = title, text = text)

    private fun digest(ordinal: Int, chapter: NovelChapter) = NovelCacheDao.ChapterDigest(
        ordinal = ordinal,
        contentHash = NovelCachePolicy.hash(chapter.title, chapter.text),
        charCount = chapter.text.length,
    )

    @Test
    fun mergeOnlyWritesChangedAndAppendedChapters() {
        val one = chapter("一", "aaa")
        val two = chapter("二", "bbb")
        val incoming = listOf(
            one,
            chapter("二", "bbb-changed"),
            chapter("三", "ccc"),
        )

        val plan = NovelCachePolicy.mergePlan(listOf(digest(0, one), digest(1, two)), incoming)

        assertEquals(listOf(1, 2), plan.upserts.map { it.ordinal })
        assertEquals(3, plan.chapterCount)
        assertEquals(incoming.sumOf { it.text.length }, plan.charCount)
    }

    @Test
    fun unchangedContentNeedsNoWrite() {
        val chapters = listOf(chapter("一", "aaa"), chapter("二", "bbb"))
        val plan = NovelCachePolicy.mergePlan(
            chapters.mapIndexed { index, c -> digest(index, c) },
            chapters,
        )
        assertTrue(plan.upserts.isEmpty())
        assertEquals(2, plan.chapterCount)
    }

    @Test
    fun appendingChaptersKeepsReadingPosition() {
        val tenChapters = List(10) { 100 }
        val twelveChapters = List(12) { 100 }

        // 10 章读到 50% = 第 5 章末；涨到 12 章后应落在同一点，即 41%
        assertEquals(41, NovelCachePolicy.rescalePercent(tenChapters, twelveChapters, 50))
        // 读完的落点是旧末尾，不是新末尾：新章还没读
        assertEquals(83, NovelCachePolicy.rescalePercent(tenChapters, twelveChapters, 100))
        assertEquals(0, NovelCachePolicy.rescalePercent(tenChapters, twelveChapters, 0))
    }
}
