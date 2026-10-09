package com.piku.client.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

/** 图墙列数：手机 3 列（钉点击密度的），屏更宽加列 */
class TrendingColumnsTest {

    @Test
    fun phoneUsesThreeColumns() {
        assertEquals(3, trendingColumns(360))
        assertEquals(3, trendingColumns(411))
    }

    @Test
    fun widerScreensAddColumns() {
        assertEquals(4, trendingColumns(480))
        assertEquals(5, trendingColumns(600))
    }

    @Test
    fun neverBelowThreeColumns() {
        assertEquals(3, trendingColumns(320))
    }
}
