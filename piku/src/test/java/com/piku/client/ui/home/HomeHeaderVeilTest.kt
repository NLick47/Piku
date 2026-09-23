package com.piku.client.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 头部让位规则：自定义背景下列表停在顶部时底衬必须完全退场（把头图让出来），
 * 其余组合一律保持旧的铺底行为；着色数值与浮层化之前逐值一致。
 */
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
    fun translucentDarkHeaderKeepsOldValues() {
        val calm = headerTintAlphas(translucent = true, dark = true, deepen = 0f)
        assertEquals(0.16f, calm.top, 0.0001f)
        assertEquals(0.10f, calm.mid, 0.0001f)
        val deep = headerTintAlphas(translucent = true, dark = true, deepen = 1f)
        assertEquals(0.21f, deep.top, 0.0001f)
        assertEquals(0.14f, deep.mid, 0.0001f)
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
