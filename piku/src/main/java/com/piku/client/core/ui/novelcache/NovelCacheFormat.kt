package com.piku.client.core.ui.novelcache

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.piku.client.R
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

internal const val MB = 1024L * 1024L

/** 缓存体积：大于 1 MB 给一位小数，否则按 KB 取整 */
@Composable
internal fun novelCacheSize(bytes: Long): String =
    if (bytes >= MB) {
        stringResource(
            R.string.novel_cache_size_mb,
            String.format(Locale.getDefault(), "%.1f", bytes / MB.toDouble()),
        )
    } else {
        val kb = if (bytes <= 0) 0 else ((bytes + 1023) / 1024).coerceAtLeast(1)
        stringResource(R.string.novel_cache_size_kb, kb.toString())
    }

/** 「今天 / 昨天 / N 天前 / 日期」——按本地日历算，不用小时数糊弄 */
@Composable
internal fun novelCacheTime(now: Long, at: Long): String {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0 -> stringResource(R.string.novel_cache_time_today)
        days == 1L -> stringResource(R.string.novel_cache_time_yesterday)
        days < 30 -> stringResource(R.string.novel_cache_time_days, days.toInt())
        else -> stringResource(
            R.string.novel_cache_time_date,
            day.year,
            day.monthValue,
            day.dayOfMonth,
        )
    }
}

/** 阅读器设置面板里那行只读状态；没有缓存时不给这行 */
@Composable
internal fun novelCacheReaderStatus(state: NovelCacheUiState): String? {
    val cached = state as? NovelCacheUiState.Cached ?: return null
    return if (cached.chapterCount > 1) {
        stringResource(R.string.novel_cache_reader_cached, cached.chapterCount)
    } else {
        stringResource(R.string.novel_cache_reader_cached_single)
    }
}
