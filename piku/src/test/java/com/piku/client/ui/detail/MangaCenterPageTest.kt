package com.piku.client.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaCenterPageTest {

    /** 一屏能看到一页多时滚到最底：中心落在最后一页。用第一个可见项算的话这里会返回倒数第二页 */
    @Test
    fun bottomOfScrollResolvesToLastPage() {
        val visible = listOf(
            VisibleSpan(index = 6, offset = -220, size = 620),
            VisibleSpan(index = 7, offset = 400, size = 620),
        )
        assertEquals(7, centerItemIndex(0, 1000, visible))
    }

    /** 长条图一屏一页：中心落在自己身上 */
    @Test
    fun singleTallPageResolvesToItself() {
        val visible = listOf(VisibleSpan(index = 3, offset = -150, size = 1500))
        assertEquals(3, centerItemIndex(0, 900, visible))
    }

    /** 中心正好压在两项交界上：归下面那一项，不能返回 null */
    @Test
    fun boundaryCenterFallsToNextItem() {
        val visible = listOf(
            VisibleSpan(index = 1, offset = 0, size = 500),
            VisibleSpan(index = 2, offset = 500, size = 500),
        )
        assertEquals(2, centerItemIndex(0, 1000, visible))
    }

    /** 首帧还没量到布局：不猜，调用方回落第一个可见项 */
    @Test
    fun emptyLayoutHasNoCenterPage() {
        assertNull(centerItemIndex(0, 1000, emptyList()))
    }
}
