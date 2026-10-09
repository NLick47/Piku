package com.piku.client.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSliderMathTest {

    private val fontSizeMin = 13f
    private val fontSizeMax = 24f
    private val lineHeightMin = 1.3f
    private val lineHeightMax = 2.4f
    private val brightnessMin = 0.05f

    /** 手在两端：读数必须是区间端点，不能差一档 */
    @Test
    fun sliderEndsLandOnTheRangeEnds() {
        assertEquals(fontSizeMin, readerStep(0f, fontSizeMin, fontSizeMax, 1f), 1e-4f)
        assertEquals(fontSizeMax, readerStep(1f, fontSizeMin, fontSizeMax, 1f), 1e-4f)
        assertEquals(0f, readerFraction(fontSizeMin, fontSizeMin, fontSizeMax), 1e-4f)
        assertEquals(1f, readerFraction(fontSizeMax, fontSizeMin, fontSizeMax), 1e-4f)
    }

    /** 落回字号要取整档：出现过 16.37sp 就是这里没夹住 */
    @Test
    fun fontSizeSnapsToWholeSteps() {
        assertEquals(19f, readerStep(0.5f, fontSizeMin, fontSizeMax, 1f), 1e-4f)
        assertEquals(16f, readerStep(readerFraction(16f, fontSizeMin, fontSizeMax), fontSizeMin, fontSizeMax, 1f), 1e-4f)
        // 相邻两档之间任意位置都只能落在这两档上
        for (i in 0..110) {
            val value = readerStep(i / 110f, fontSizeMin, fontSizeMax, 1f)
            assertEquals(value, value.toInt().toFloat(), 1e-4f)
            assertTrue(value in fontSizeMin..fontSizeMax)
        }
    }

    @Test
    fun lineHeightKeepsOneDecimal() {
        val step = readerStep(0.5f, lineHeightMin, lineHeightMax, 0.1f)
        assertEquals(1.9f, step, 1e-4f)
        assertEquals(1.3f, readerStep(0f, lineHeightMin, lineHeightMax, 0.1f), 1e-4f)
        assertEquals(2.4f, readerStep(1f, lineHeightMin, lineHeightMax, 0.1f), 1e-4f)
    }

    /** 越界的比例（手指划出控件）不能把读数带出区间 */
    @Test
    fun outOfRangeFractionIsClamped() {
        assertEquals(fontSizeMax, readerStep(3f, fontSizeMin, fontSizeMax, 1f), 1e-4f)
        assertEquals(fontSizeMin, readerStep(-2f, fontSizeMin, fontSizeMax, 1f), 1e-4f)
        assertEquals(1f, readerFraction(999f, fontSizeMin, fontSizeMax), 1e-4f)
        assertEquals(0f, readerFraction(-999f, fontSizeMin, fontSizeMax), 1e-4f)
    }

    /** 亮度最低也要看得见字：区间下限不能是 0（全黑），0 只留给"跟随系统" */
    @Test
    fun brightnessNeverReachesZero() {
        val min = readerStep(0f, brightnessMin, 1f, 0.05f)
        assertEquals(brightnessMin, min, 1e-4f)
        assertTrue(min > 0f)
        assertEquals(1f, readerStep(1f, brightnessMin, 1f, 0.05f), 1e-4f)
    }

    /** 上下限相等（配置被改坏）时不能除零，也不能崩 */
    @Test
    fun degenerateRangeFallsBackToTheLowerBound() {
        assertEquals(0f, readerFraction(5f, 2f, 2f), 1e-4f)
        assertEquals(2f, readerStep(0.5f, 2f, 2f, 0.1f), 1e-4f)
        assertEquals(2f, readerStep(0.5f, 2f, 3f, 0f), 1e-4f)
    }

    /** 往返：把读数放回滑块再落回来，还必须是同一个读数 */
    @Test
    fun readingRoundTripsThroughTheSlider() {
        for (size in 13..24) {
            val value = size.toFloat()
            assertEquals(
                value,
                readerStep(readerFraction(value, fontSizeMin, fontSizeMax), fontSizeMin, fontSizeMax, 1f),
                1e-4f,
            )
        }
        for (tenths in 0..11) {
            val value = lineHeightMin + tenths * 0.1f
            assertEquals(
                value,
                readerStep(readerFraction(value, lineHeightMin, lineHeightMax), lineHeightMin, lineHeightMax, 0.1f),
                1e-4f,
            )
        }
    }
}
