package com.piku.client.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageHeightForAspectTest {

    private val screenWidth = 360
    private val authorFirstWidth = screenWidth - CONTENT_PADDING_DP * 2

    /** 宽高比还没量出来：用默认占位高（骨架拿到缩略图之前就是这一档） */
    @Test
    fun unmeasuredAspectKeepsTheDefaultHeight() {
        assertEquals(
            IMAGE_HEIGHT_DEFAULT_DP,
            imageHeightForAspect(aspect = 0f, availableWidthDp = authorFirstWidth),
        )
    }

    /** 超宽横图不至于被压成一条 */
    @Test
    fun wideImageStopsAtTheMinimumHeight() {
        assertEquals(
            IMAGE_HEIGHT_MIN_DP,
            imageHeightForAspect(aspect = 4f, availableWidthDp = authorFirstWidth),
        )
    }

    /** 超长竖图不至于顶满整屏（记录卡式版面） */
    @Test
    fun tallImageStopsAtTheMaximumHeight() {
        assertEquals(
            IMAGE_HEIGHT_MAX_DP,
            imageHeightForAspect(aspect = 0.2f, availableWidthDp = authorFirstWidth),
        )
    }

    /** 中间那一段按可用宽度换算 */
    @Test
    fun normalAspectFollowsTheAvailableWidth() {
        assertEquals(427, imageHeightForAspect(aspect = 0.75f, availableWidthDp = authorFirstWidth))
        assertEquals(280, imageHeightForAspect(aspect = 1f, availableWidthDp = 280))
    }

    /** 通栏给定更宽的可用宽度与上限：同一个宽高比，p站版比记录卡式更高 */
    @Test
    fun fullBleedGetsMoreWidthAndAHigherCap() {
        val fullBleedCap = fullBleedMaxHeightDp(screenHeightDp = 800)

        assertEquals(
            480,
            imageHeightForAspect(aspect = 0.75f, availableWidthDp = screenWidth, maxHeightDp = fullBleedCap),
        )
        assertTrue(
            "通栏的上限必须高于记录卡式，否则放松高度上限这件事没发生",
            fullBleedCap > IMAGE_HEIGHT_MAX_DP,
        )
        assertEquals(
            fullBleedCap,
            imageHeightForAspect(aspect = 0.1f, availableWidthDp = screenWidth, maxHeightDp = fullBleedCap),
        )
    }

    /** 上限只该比记录卡式更大：矮屏上也不回落，通栏图的余地不能反而变小 */
    @Test
    fun fullBleedCapFollowsScreenHeight() {
        assertEquals(600, fullBleedMaxHeightDp(screenHeightDp = 800))
        assertEquals(IMAGE_HEIGHT_MAX_DP, fullBleedMaxHeightDp(screenHeightDp = 640))
        assertEquals(IMAGE_HEIGHT_MAX_DP, fullBleedMaxHeightDp(screenHeightDp = 480))
    }
}
