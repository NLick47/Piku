package com.piku.client.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ReaderProgressMathTest {

    /** 正文不足一屏：没有可滚的像素，旧版口径就是"已读完" */
    @Test
    fun shortTextCountsAsFullyRead() {
        assertEquals(100, readerProgressPercent(0, 0))
        assertEquals(100, readerProgressPercent(300, -5))
        assertEquals(0, readerScrollTarget(50, 0))
        assertEquals(0, readerScrollTarget(50, -5))
    }

    @Test
    fun valuesOutsideTheScrollRangeAreClamped() {
        assertEquals(0, readerProgressPercent(-400, 1000))
        assertEquals(100, readerProgressPercent(99_999, 1000))
        assertEquals(0, readerScrollTarget(-20, 1000))
        assertEquals(1000, readerScrollTarget(300, 1000))
    }

    /**
     * 存百分比 → 还原 → 再算百分比，必须回到同一档附近：差得越多，改字号后跑位越远。
     * 实测定界：可滚范围 ≥200px 时最坏漂移 1 个点（两次取整各丢不到 1%）；范围只有几十像素
     * （正文刚好比一屏多一点）时才可能差 2 个点。
     */
    @Test
    fun restoringAPercentKeepsTheSamePercent() {
        for (max in intArrayOf(200, 997, 4000, 65_535)) {
            for (percent in 0..100) {
                val restored = readerProgressPercent(readerScrollTarget(percent, max), max)
                assertTrue(
                    "max=$max 存 $percent% 还原成 $restored%",
                    abs(restored - percent) <= 1,
                )
            }
        }
    }

    /** 还原目标必须落在可滚范围内：越界会被 ScrollState 拒到边上，读者就被甩到段首/段尾 */
    @Test
    fun restoreTargetStaysInsideTheScrollRange() {
        for (max in intArrayOf(0, 1, 50, 4000)) {
            for (percent in -10..110) {
                val target = readerScrollTarget(percent, max)
                assertTrue("max=$max percent=$percent target=$target", target in 0..maxOf(max, 0))
            }
        }
    }
}
