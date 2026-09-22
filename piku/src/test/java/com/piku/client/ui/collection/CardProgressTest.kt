package com.piku.client.ui.collection

import com.piku.client.domain.model.ReadingProgress
import com.piku.client.domain.model.Work
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 卡片进度条的换算规则：什么时候不画、页码怎么截断、图集与小说谁优先。
 */
class CardProgressTest {

    private fun work(imageCount: Int) = Work(
        id = 1L,
        authorId = 2L,
        authorName = "作者",
        authorAvatarUrl = null,
        categoryCd = 0,
        categoryName = "分类",
        title = "标题",
        thumbnailUrl = "",
        imageCount = imageCount,
        r18 = false,
    )

    @Test
    fun noProgressMeansNoBar() {
        assertNull(null.toCardProgress(work(imageCount = 10)))
        assertNull(ReadingProgress().toCardProgress(work(imageCount = 10)))
    }

    @Test
    fun firstImagePageIsNotTreatedAsProgress() {
        // 打开详情点第一张图是常规操作，不该被当成"读到过"
        assertNull(ReadingProgress(imagePage = 1).toCardProgress(work(imageCount = 10)))
    }

    @Test
    fun imagePageBecomesFractionAndLabel() {
        val progress = ReadingProgress(imagePage = 5).toCardProgress(work(imageCount = 12))

        assertEquals("5/12", progress?.label)
        assertEquals(5f / 12f, progress!!.fraction, 0.0001f)
    }

    @Test
    fun imagePageIsClampedToKnownTotal() {
        // 作品后来又少了图（作者删图）时，页码不能超过总页数
        val progress = ReadingProgress(imagePage = 20).toCardProgress(work(imageCount = 12))

        assertEquals("12/12", progress?.label)
        assertEquals(1f, progress!!.fraction, 0.0001f)
    }

    @Test
    fun unknownImageCountDrawsNothing() {
        // imageCount 为 0/1 时算不出比例，宁可没有进度条也不要画成满格或 0 格
        assertNull(ReadingProgress(imagePage = 5).toCardProgress(work(imageCount = 0)))
        assertNull(ReadingProgress(imagePage = 5).toCardProgress(work(imageCount = 1)))
    }

    @Test
    fun novelPercentIsUsedWhenThereIsNoPage() {
        val progress = ReadingProgress(novelPercent = 42).toCardProgress(work(imageCount = 0))

        assertEquals("42%", progress?.label)
        assertEquals(0.42f, progress!!.fraction, 0.0001f)
    }

    @Test
    fun imagePageWinsWhenBothAreRecorded() {
        val both = ReadingProgress(novelPercent = 42, imagePage = 3)

        assertEquals("3/10", both.toCardProgress(work(imageCount = 10))?.label)
    }
}
