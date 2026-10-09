package com.piku.client.ui.home.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHeaderVeilTest {

    @Test
    fun customBackgroundAtTopClearsTheHeader() {
        assertEquals(0f, headerVeilTarget(translucent = true, atTop = true), 0.0001f)
    }

    @Test
    fun customBackgroundVeilsBackOnceScrolled() {
        assertEquals(1f, headerVeilTarget(translucent = true, atTop = false), 0.0001f)
    }

    @Test
    fun defaultBackgroundStaysVeiled() {
        assertEquals(1f, headerVeilTarget(translucent = false, atTop = true), 0.0001f)
        assertEquals(1f, headerVeilTarget(translucent = false, atTop = false), 0.0001f)
    }
}

class HomeHeaderTintTest {

    @Test
    fun translucentLightHeaderStaysClear() {
        val tint = headerTintAlphas(translucent = true, dark = false, deepen = 0f)
        assertEquals(0f, tint.top, 0.0001f)
        assertEquals(0f, tint.mid, 0.0001f)
    }

    @Test
    fun translucentHeaderStaysClearOnBothThemes() {
        for (dark in listOf(false, true)) {
            val calm = headerTintAlphas(translucent = true, dark = dark, deepen = 0f)
            assertEquals(0f, calm.top, 0.0001f)
            assertEquals(0f, calm.mid, 0.0001f)
            val deep = headerTintAlphas(translucent = true, dark = dark, deepen = 1f)
            assertEquals(0f, deep.top, 0.0001f)
            assertEquals(0f, deep.mid, 0.0001f)
        }
    }

    @Test
    fun opaqueHeaderWearsTheGlassChipMaterial() {
        val light = headerTintAlphas(translucent = false, dark = false, deepen = 0f)
        val dark = headerTintAlphas(translucent = false, dark = true, deepen = 0f)
        assertEquals(0.90f, light.top, 0.0001f)
        assertEquals(0.78f, light.mid, 0.0001f)
        assertEquals(0.38f, dark.top, 0.0001f)
        assertEquals(0.28f, dark.mid, 0.0001f)
    }

    @Test
    fun darkHeaderLeavesRoomForTheSearchButton() {
        val dark = headerTintAlphas(translucent = false, dark = true, deepen = 0f)
        val light = headerTintAlphas(translucent = false, dark = false, deepen = 0f)
        assertTrue("暗色底衬应比亮色更透：$dark vs $light", dark.top < light.top)
        assertTrue("暗色底衬要给搜索钮留出落差：$dark", dark.top < 0.5f)
    }

    @Test
    fun scrollingThickensTheHeader() {
        for (dark in listOf(false, true)) {
            val calm = headerTintAlphas(translucent = false, dark = dark, deepen = 0f)
            val deep = headerTintAlphas(translucent = false, dark = dark, deepen = 1f)
            assertTrue("滚动时顶边应更实：$deep vs $calm", deep.top > calm.top)
            assertTrue("滚动时中段应更实：$deep vs $calm", deep.mid > calm.mid)
            assertTrue("压实后也不该糊成实色：$deep", deep.top < 1f)
        }
    }
}
