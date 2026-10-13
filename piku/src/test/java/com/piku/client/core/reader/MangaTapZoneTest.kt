package com.piku.client.core.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaTapZoneTest {

    private val viewport = IntSize(width = 1080, height = 2000)

    /** 左侧三分之一：上一页（右→左时下一页由调用方对调） */
    @Test
    fun leftThirdIsPreviousZone() {
        assertEquals(TAP_ZONE_LEFT, tapZone(Offset(0f, 900f), viewport))
        assertEquals(TAP_ZONE_LEFT, tapZone(Offset(359f, 900f), viewport))
    }

    /** 右侧三分之一：下一页 */
    @Test
    fun rightThirdIsNextZone() {
        assertEquals(TAP_ZONE_RIGHT, tapZone(Offset(721f, 900f), viewport))
        assertEquals(TAP_ZONE_RIGHT, tapZone(Offset(1080f, 900f), viewport))
    }

    /** 中间三分之一留给显隐与双击：判错会让双击缩放失效或点哪儿都翻页 */
    @Test
    fun middleThirdStaysForControls() {
        assertEquals(TAP_ZONE_CENTER, tapZone(Offset(360f, 900f), viewport))
        assertEquals(TAP_ZONE_CENTER, tapZone(Offset(540f, 900f), viewport))
        assertEquals(TAP_ZONE_CENTER, tapZone(Offset(720f, 900f), viewport))
    }

    /** 视口还没量到宽度（首帧）不判热区，否则会把整屏当中间 */
    @Test
    fun unmeasuredViewportFallsBackToCenter() {
        assertEquals(TAP_ZONE_CENTER, tapZone(Offset(0f, 0f), IntSize.Zero))
    }
}
