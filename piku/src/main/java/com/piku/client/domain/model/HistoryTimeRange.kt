package com.piku.client.domain.model

import com.piku.client.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 浏览记录的时间范围筛选。
 *
 * [days] 表示向前滚动的天数窗口（0 = 全部）；
 * 「今天」特殊处理为按本地时区当天 0 点起算，而非滚动 24 小时。
 */
enum class HistoryTimeRange(val days: Int) {
    ALL(0),
    TODAY(1),
    THREE_DAYS(3),
    ONE_WEEK(7),
    ONE_MONTH(30),
    THREE_MONTHS(90),
    ;

    fun labelRes(): Int = when (this) {
        ALL -> R.string.history_range_all
        TODAY -> R.string.history_range_today
        THREE_DAYS -> R.string.history_range_3d
        ONE_WEEK -> R.string.history_range_1w
        ONE_MONTH -> R.string.history_range_1m
        THREE_MONTHS -> R.string.history_range_3m
    }

    /** 筛选起始时间戳（毫秒），早于该时间的记录将被过滤掉；不早于该时间的记录保留 */
    fun cutoffMillis(now: Long = System.currentTimeMillis()): Long = when (this) {
        ALL -> 0L
        TODAY -> {
            val zone = ZoneId.systemDefault()
            LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        }
        else -> now - days * DAY_MILLIS
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
