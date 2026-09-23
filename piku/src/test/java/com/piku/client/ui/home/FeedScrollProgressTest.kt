package com.piku.client.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 底边进度线的长度：停在顶部必须是 0，滚到底才是满格。 */
class FeedScrollProgressTest {

    @Test
    fun topOfListIsEmpty() {
        assertEquals(0f, scrollProgressOf(0, 0, 300, totalItems = 20), 0.0001f)
    }

    @Test
    fun bottomOfListIsFull() {
        assertEquals(1f, scrollProgressOf(19, -300, 300, totalItems = 20), 0.0001f)
    }

    @Test
    fun halfScrolledItemCountsAsHalf() {
        assertEquals(0.475f, scrollProgressOf(9, -150, 300, totalItems = 20), 0.0001f)
    }

    @Test
    fun emptyGridHasNoProgress() {
        assertEquals(0f, scrollProgressOf(0, 0, 0, totalItems = 0), 0.0001f)
    }

    @Test
    fun progressGrowsWithScroll() {
        val values = (0..20).map { index ->
            scrollProgressOf(index, -150, 300, totalItems = 20)
        }
        assertTrue(values.zipWithNext().all { (a, b) -> b >= a })
    }
}
