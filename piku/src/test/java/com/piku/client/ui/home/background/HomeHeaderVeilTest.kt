package com.piku.client.ui.home.background

import org.junit.Assert.assertEquals
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

/** 头部底衬着色：必须逐项复现改动前的数值，头部仍在布局流里，滚动行为不变。 */
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
    fun opaqueHeaderKeepsOldValues() {
        assertEquals(
            0.50f,
            headerTintAlphas(translucent = false, dark = true, deepen = 0f).top,
            0.0001f,
        )
        assertEquals(
            0.32f,
            headerTintAlphas(translucent = false, dark = true, deepen = 0f).mid,
            0.0001f,
        )
        assertEquals(
            0.95f,
            headerTintAlphas(translucent = false, dark = false, deepen = 0f).top,
            0.0001f,
        )
        assertEquals(
            0.80f,
            headerTintAlphas(translucent = false, dark = false, deepen = 0f).mid,
            0.0001f,
        )
        assertEquals(
            0.88f,
            headerTintAlphas(translucent = false, dark = false, deepen = 1f).mid,
            0.0001f,
        )
    }
}
