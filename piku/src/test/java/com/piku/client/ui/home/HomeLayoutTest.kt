package com.piku.client.ui.home

import com.piku.client.ui.theme.PikuLayout
import com.piku.client.ui.theme.feedCardWidthPx
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Test

/** 首页横向几何：导航可见边缘必须落在网格内容边缘上，缩略图预热必须用同一套卡片宽。 */
class HomeLayoutTest {

    @Test
    fun navVisibleEdgesLandOnContentInset() {
        val navVisualInset = PikuLayout.NavRowInset + (PikuLayout.NavHit - PikuLayout.NavControl) / 2
        assertEquals(PikuLayout.ScreenInset, navVisualInset)
    }

    @Test
    fun screenWidthMinusChromeIsTwoCards() {
        assertEquals(PikuLayout.ScreenInset * 2 + PikuLayout.GridGap, PikuLayout.CardWidthChrome)
        assertEquals(44f, PikuLayout.CardWidthChrome.value, 0.001f)
    }

    @Test
    fun prefetchSideFollowsCardWidth() {
        // 360dp / 2x：(360-44)/2 = 158dp 卡片宽 → 316px
        assertEquals(316f, feedCardWidthPx(screenWidthDp = 360, density = 2f), 0.001f)
        // 本机 354dp / 3.5x：(354-44)/2 = 155dp → 542px，被预热上限收到 512
        assertEquals(
            512,
            feedCardWidthPx(screenWidthDp = 354, density = 3.5f).roundToInt().coerceIn(256, 512),
        )
    }
}
