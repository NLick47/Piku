package com.piku.client.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeParallaxTest {

    @Test
    fun topOfListHasNoOffset() {
        assertEquals(0f, heroParallaxOffsetPx(0), 0.0001f)
    }

    @Test
    fun offsetGrowsWithScroll() {
        val values = (0..12).map { i -> heroParallaxOffsetPx(i * 30) }
        assertTrue(values.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun offsetCapsAtMax() {
        assertEquals(HERO_PARALLAX_MAX_PX, heroParallaxOffsetPx(10_000), 0.0001f)
        // 封顶后继续滚动，位移完全不变（到位即停）
        assertEquals(
            heroParallaxOffsetPx(HERO_PARALLAX_MAX_PX.toInt()),
            heroParallaxOffsetPx(HERO_PARALLAX_MAX_PX.toInt() + 2_000),
            0.0001f,
        )
    }

    @Test
    fun offsetStaysWithinMax() {
        assertEquals(90f, HERO_PARALLAX_MAX_PX, 0.0001f)
        assertTrue(heroParallaxOffsetPx(600) <= HERO_PARALLAX_MAX_PX)
    }

    @Test
    fun negativeScrollIsClamped() {
        assertEquals(0f, heroParallaxOffsetPx(-300), 0.0001f)
    }

    @Test
    fun backdropCapIsWellBelowSafeBound() {
        // 毛玻璃层 scale 可低到 1.0（无放大余量），24px 漂移在 1080p 屏上占 2.2%，
        // 模糊本身会把这个边缘吃掉，任何用户参数下不露底
        assertTrue(BACKDROP_PARALLAX_MAX_PX < HERO_PARALLAX_MAX_PX / 3f)
    }

    @Test
    fun sourceIsMonotonicAcrossFirstItemSwap() {
        // 快速下滑模拟序列：item 0 深负后首个可见 item 更替为 1、2……
        // 旧实现读「首个可见 item」的 offset，会在更替瞬间从 90 跌回 50 造成抽搐
        val scrollDown = listOf(
            0 to 26,    // 停顶（contentPadding）
            0 to -20,
            0 to -90,
            0 to -500,  // 滚出前早已封顶
            1 to -50,   // 旧实现此处跌回 50
            2 to -400,
            5 to -30,
        )
        val values = scrollDown.map { (index, offsetY) -> parallaxSourcePx(index, offsetY) }
        assertTrue(values.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun sourceContinuousWhenItem0LeavesViewport() {
        // item 0 滚出视口的瞬间不跳变：两侧都是封顶值
        assertEquals(parallaxSourcePx(0, -10_000), parallaxSourcePx(1, 0))
        assertEquals(parallaxSourcePx(0, -10_000), parallaxSourcePx(3, -700))
    }

    @Test
    fun sourceZeroAtTopAndClampedBelowZero() {
        assertEquals(0, parallaxSourcePx(0, 26))
        assertEquals(0, parallaxSourcePx(0, 500))
        assertEquals(0, parallaxSourcePx(0, 0))
    }

    @Test
    fun sourceMonotonicBackUpToZero() {
        val backUp = listOf(
            5 to -30,
            1 to -50,
            0 to -500,
            0 to -40,
            0 to -1,
            0 to 26,
        )
        val values = backUp.map { (index, offsetY) -> parallaxSourcePx(index, offsetY) }
        assertTrue(values.zipWithNext().all { (a, b) -> b <= a })
        assertEquals(0, values.last())
    }
}
