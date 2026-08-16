package com.piku.client.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HistoryTimeRangeTest {

    // 固定一个"现在"：2024-06-15 12:34:56 UTC
    private val now = Instant.parse("2024-06-15T12:34:56Z").toEpochMilli()
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun allKeepsEverything() {
        assertEquals(0L, HistoryTimeRange.ALL.cutoffMillis(now))
    }

    @Test
    fun fixedDayRangesUseRollingWindow() {
        assertEquals(now - 3 * day, HistoryTimeRange.THREE_DAYS.cutoffMillis(now))
        assertEquals(now - 7 * day, HistoryTimeRange.ONE_WEEK.cutoffMillis(now))
        assertEquals(now - 30 * day, HistoryTimeRange.ONE_MONTH.cutoffMillis(now))
        assertEquals(now - 90 * day, HistoryTimeRange.THREE_MONTHS.cutoffMillis(now))
    }

    @Test
    fun todayStartsFromLocalMidnight() {
        val cutoff = HistoryTimeRange.TODAY.cutoffMillis(now)
        val zone = ZoneId.systemDefault()
        val expected = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        assertEquals(expected, cutoff)
        // 今天 0 点一定在"现在"之前，且不早于 24 小时窗口（时区偏移最多 ±14h）
        assertTrue(cutoff <= now)
        assertTrue(cutoff >= now - 38 * 60 * 60 * 1000L)
    }

    @Test
    fun cutoffAlwaysAtMostNow() {
        HistoryTimeRange.entries.forEach { range ->
            assertTrue(range.cutoffMillis(now) <= now)
        }
    }
}
