package com.piku.client.core.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaTapZoneTest {

    private val viewport = IntSize(width = 1080, height = 2000)

    /** 左侧 30%：上一页（右→左时下一页由调用方对调） */
    @Test
    fun leftEdgeIsPreviousZone() {
        assertEquals(TAP_ZONE_LEFT, tapZone(Offset(0f, 900f), viewport))
        assertEquals(TAP_ZONE_LEFT, tapZone(Offset(323f, 900f), viewport))
    }

    /** 右侧 30%：下一页 */
    @Test
    fun rightEdgeIsNextZone() {
        assertEquals(TAP_ZONE_RIGHT, tapZone(Offset(757f, 900f), viewport))
        assertEquals(TAP_ZONE_RIGHT, tapZone(Offset(1080f, 900f), viewport))
    }

    /** 中间 40% 留给显隐：边界错一格就变成"点中间却翻页"，菜单再也叫不出来 */
    @Test
    fun centerBandIsWiderThanEdges() {
        assertEquals(TAP_ZONE_CENTER, tapZone(Offset(324f, 900f), viewport))
        assertEquals(TAP_ZONE_CENTER, tapZone(Offset(540f, 900f), viewport))
        assertEquals(TAP_ZONE_CENTER, tapZone(Offset(756f, 900f), viewport))
    }

    /** 视口还没量到宽度（首帧）不判热区，否则会把整屏当中间 */
    @Test
    fun unmeasuredViewportFallsBackToCenter() {
        assertEquals(TAP_ZONE_CENTER, tapZone(Offset(0f, 0f), IntSize.Zero))
    }
}
