package com.piku.client.ui.source

import com.piku.client.domain.model.Work
import org.junit.Assert.assertEquals
import org.junit.Test

class CardAspectRatioTest {

    private fun work(width: Int, height: Int) = Work(
        id = 1,
        authorId = 2,
        authorName = "",
        authorAvatarUrl = null,
        categoryCd = -1,
        categoryName = "",
        title = "",
        thumbnailUrl = "",
        thumbWidth = width,
        thumbHeight = height,
        imageCount = 1,
        r18 = false,
    )

    @Test
    fun missingSizeFallsBackToSquare() {
        assertEquals(1f, cardAspectRatio(work(0, 0)), 0.001f)
    }

    @Test
    fun portraitKeepsItsOwnRatio() {
        assertEquals(0.667f, cardAspectRatio(work(1200, 1800)), 0.001f)
    }

    @Test
    fun landscapeKeepsItsOwnRatio() {
        assertEquals(1.5f, cardAspectRatio(work(1800, 1200)), 0.001f)
    }

    @Test
    fun aVeryLongStripIsClampedSoOneCardCannotTakeTheWholeScreen() {
        assertEquals(0.56f, cardAspectRatio(work(1000, 20000)), 0.001f)
    }

    @Test
    fun aVeryWideStripIsClampedToo() {
        assertEquals(1.8f, cardAspectRatio(work(20000, 1000)), 0.001f)
    }
}
