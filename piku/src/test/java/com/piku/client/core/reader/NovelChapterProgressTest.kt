package com.piku.client.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelChapterProgressTest {

    /** 长短悬殊：中篇 1000 字 + 长篇 3000 字 */
    private val uneven = listOf(1000, 3000)

    @Test
    fun overallWeighsChaptersByLength() {
        // 第一章占整部的 1/4：它的 100% 就是整部的 25%
        assertEquals(25, NovelChapterProgress.overall(uneven, chapterIndex = 0, localPercent = 100))
        assertEquals(12, NovelChapterProgress.overall(uneven, chapterIndex = 0, localPercent = 50))
        assertEquals(25, NovelChapterProgress.overall(uneven, chapterIndex = 1, localPercent = 0))
        assertEquals(100, NovelChapterProgress.overall(uneven, chapterIndex = 1, localPercent = 100))
    }

    /** 咬人：按章数均分的实现会让第一章 100% 变成 50%（两章各半），这里必须是 25 */
    @Test
    fun lengthWeightedNotCountWeighted() {
        assertTrue(
            "第一章只占 1/4 篇幅，不该按章数均分成 50%",
            NovelChapterProgress.overall(uneven, 0, 100) < 50,
        )
    }

    @Test
    fun locateFindsChapterAndLocalPercent() {
        val start = NovelChapterProgress.locate(uneven, 0)
        assertEquals(0, start.chapterIndex)
        assertEquals(0, start.localPercent)

        val end = NovelChapterProgress.locate(uneven, 100)
        assertEquals(1, end.chapterIndex)
        assertEquals(100, end.localPercent)

        // 25% 恰好是第二章开头（第一章 100% 处）——落在第二章 0%
        val boundary = NovelChapterProgress.locate(uneven, 25)
        assertEquals(1, boundary.chapterIndex)
        assertEquals(0, boundary.localPercent)

        // 12% 在第一章内部：1000 字里走了 480 字
        val inside = NovelChapterProgress.locate(uneven, 12)
        assertEquals(0, inside.chapterIndex)
        assertEquals(48, inside.localPercent)
    }

    @Test
    fun locateAndOverallRoundTrip() {
        val lengths = listOf(300, 900, 2000, 4500, 120)
        for (percent in 0..100) {
            val pos = NovelChapterProgress.locate(lengths, percent)
            val back = NovelChapterProgress.overall(lengths, pos.chapterIndex, pos.localPercent)
            assertTrue(
                "整部 $percent% 往返后得 $back%（章 ${pos.chapterIndex} 内 ${pos.localPercent}%）",
                kotlin.math.abs(back - percent) <= 1,
            )
        }
    }

    @Test
    fun emptyChaptersYieldZero() {
        assertEquals(NovelChapterProgress.Position(0, 0), NovelChapterProgress.locate(emptyList(), 42))
        assertEquals(0, NovelChapterProgress.overall(emptyList(), 3, 50))
    }

    @Test
    fun outOfRangeInputsAreClamped() {
        assertEquals(NovelChapterProgress.Position(0, 0), NovelChapterProgress.locate(uneven, -10))
        assertEquals(NovelChapterProgress.Position(1, 100), NovelChapterProgress.locate(uneven, 150))
        assertEquals(0, NovelChapterProgress.overall(uneven, 0, -5))
        assertEquals(100, NovelChapterProgress.overall(uneven, 9, 100))
    }

    @Test
    fun singleChapterMapsOneToOne() {
        val lengths = listOf(5000)
        assertEquals(0, NovelChapterProgress.locate(lengths, 37).chapterIndex)
        assertEquals(37, NovelChapterProgress.locate(lengths, 37).localPercent)
        assertEquals(37, NovelChapterProgress.overall(lengths, 0, 37))
    }
}
