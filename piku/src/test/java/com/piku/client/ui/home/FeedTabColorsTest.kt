package com.piku.client.ui.home

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedTabColorsTest {

    @Test
    fun noCustomBackgroundFallsBackToTheme() {
        assertNull(feedTabColors(hasCustomBackground = false, bandLuma = 0.9f))
    }

    @Test
    fun missingLumaFallsBackToTheme() {
        assertNull(feedTabColors(hasCustomBackground = true, bandLuma = null))
    }

    @Test
    fun brightBackdropYieldsDarkText() {
        val colors = feedTabColors(hasCustomBackground = true, bandLuma = 0.82f)!!
        assertTrue("亮底应压深字", luma(colors.active) < TAB_LUMA_THRESHOLD)
        assertTrue(luma(colors.inactive) < TAB_LUMA_THRESHOLD)
    }

    @Test
    fun darkBackdropYieldsLightText() {
        val colors = feedTabColors(hasCustomBackground = true, bandLuma = 0.15f)!!
        assertTrue("深底应压浅字", luma(colors.active) > TAB_LUMA_THRESHOLD)
        assertTrue(luma(colors.inactive) > TAB_LUMA_THRESHOLD)
    }

    @Test
    fun activeContrastsMoreThanInactive() {
        val light = feedTabColors(true, 0.82f)!!
        assertTrue(luma(light.active) < luma(light.inactive))
        val dark = feedTabColors(true, 0.15f)!!
        assertTrue(luma(dark.active) > luma(dark.inactive))
    }

    @Test
    fun shadowIsOppositeToText() {
        // 深字配亮影子、浅字配暗影子：底图局部突变时的保底轮廓
        val onLight = feedTabColors(true, 0.82f)!!.shadow
        assertNotNull("自定义背景必须带阴影", onLight)
        assertTrue("深字的影子要亮", luma(onLight!!) > TAB_LUMA_THRESHOLD)

        val onDark = feedTabColors(true, 0.15f)!!.shadow
        assertNotNull(onDark)
        assertTrue("浅字的影子要暗", luma(onDark!!) < TAB_LUMA_THRESHOLD)
    }

    @Test
    fun themeFallbackCarriesNoShadow() {
        // 退回主题色的路径（null）在调用侧不加阴影，头图上由毛玻璃衬底负责可读性
        assertNull(feedTabColors(hasCustomBackground = false, bandLuma = 0.2f))
        assertNull(feedTabColors(hasCustomBackground = true, bandLuma = null))
    }

    @Test
    fun tabTextShadowMapsNullToNull() {
        assertNull(tabTextShadow(null))
        assertNotNull(tabTextShadow(Color.Black))
    }

    @Test
    fun swatchesAreClampedSoCannotCarryLuma() {
        // 存图时 scrimLight 被钳到 L≥0.84、scrimDark 被钳到 L≤0.30：
        // 两者都跨不到阈值另一侧，因此不能用来判明暗（这正是旧实现失效的原因）
        assertTrue(LIGHT_SCRIM_L_MIN > TAB_LUMA_THRESHOLD)
        assertTrue(DARK_SCRIM_L_MAX < TAB_LUMA_THRESHOLD)
    }

    private fun luma(color: Color): Float =
        0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

    private companion object {
        const val LIGHT_SCRIM_L_MIN = 0.84f
        const val DARK_SCRIM_L_MAX = 0.30f
    }
}
