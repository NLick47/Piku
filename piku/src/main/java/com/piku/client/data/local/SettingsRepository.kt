package com.piku.client.data.local

import android.content.SharedPreferences
import com.piku.client.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用设置存储。
 * 使用 SharedPreferences（而非 DataStore）是为了去掉 DataStore 依赖、缩小 APK；
 * 与 [LanguageStore] 同一套模式：内存 StateFlow 为准，写后同步更新内存 + apply() 落盘。
 * 读操作全部同步内存，写操作 setter 无挂起点，调用线程上读改写原子完成。
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val prefs: SharedPreferences,
) {

    private val _showAdultContent = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_ADULT_CONTENT, false),
    )
    val showAdultContent: StateFlow<Boolean> = _showAdultContent.asStateFlow()

    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME_MODE, null)
            ?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() }
            ?: ThemeMode.SYSTEM,
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** 浏览记录保留天数，0 表示永久保留 */
    private val _historyRetentionDays = MutableStateFlow(
        prefs.getInt(KEY_HISTORY_RETENTION_DAYS, 0),
    )
    val historyRetentionDays: StateFlow<Int> = _historyRetentionDays.asStateFlow()

    /** 启动时自动检查更新 */
    private val _autoCheckEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_CHECK_ENABLED, true),
    )
    val autoCheckEnabled: StateFlow<Boolean> = _autoCheckEnabled.asStateFlow()

    /** 上次检查更新时间（epoch millis），0 表示从未检查过 */
    private val _lastUpdateCheckAt = MutableStateFlow(
        prefs.getLong(KEY_LAST_UPDATE_CHECK_AT, 0L),
    )
    val lastUpdateCheckAt: StateFlow<Long> = _lastUpdateCheckAt.asStateFlow()

    fun setShowAdultContent(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_ADULT_CONTENT, value).apply()
        _showAdultContent.value = value
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setHistoryRetentionDays(days: Int) {
        prefs.edit().putInt(KEY_HISTORY_RETENTION_DAYS, days).apply()
        _historyRetentionDays.value = days
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK_ENABLED, enabled).apply()
        _autoCheckEnabled.value = enabled
    }

    private val _novelFontSize = MutableStateFlow(
        prefs.getFloat(KEY_NOVEL_FONT_SIZE, NOVEL_FONT_DEFAULT),
    )
    val novelFontSize: StateFlow<Float> = _novelFontSize.asStateFlow()

    /** 小说阅读器浅色模式（米色纸），独立于系统主题，由用户显式切换 */
    private val _novelReaderLight = MutableStateFlow(
        prefs.getBoolean(KEY_NOVEL_READER_LIGHT, true),
    )
    val novelReaderLight: StateFlow<Boolean> = _novelReaderLight.asStateFlow()

    fun setNovelFontSize(size: Float) {
        val clamped = size.coerceIn(NOVEL_FONT_MIN, NOVEL_FONT_MAX)
        prefs.edit().putFloat(KEY_NOVEL_FONT_SIZE, clamped).apply()
        _novelFontSize.value = clamped
    }

    fun setNovelReaderLight(light: Boolean) {
        prefs.edit().putBoolean(KEY_NOVEL_READER_LIGHT, light).apply()
        _novelReaderLight.value = light
    }

    /**
     * 读取某作品的阅读进度（百分比 0~100，0 表示无进度）。
     * 用百分比而非像素偏移存储，避免字号调整后滚动位置错位。
     */
    fun getNovelProgress(workId: Long): Int =
        prefs.getInt(novelProgressKey(workId), 0).coerceIn(0, 100)

    /** 保存某作品的阅读进度（百分比 0~100） */
    fun setNovelProgress(workId: Long, percent: Int) {
        prefs.edit().putInt(novelProgressKey(workId), percent.coerceIn(0, 100)).apply()
    }

    private fun novelProgressKey(workId: Long): String = "$KEY_NOVEL_PROGRESS_PREFIX$workId"

    fun recordUpdateCheck() {
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_AT, System.currentTimeMillis()).apply()
        _lastUpdateCheckAt.value = System.currentTimeMillis()
    }

    companion object {
        const val KEY_SHOW_ADULT_CONTENT = "show_adult_content"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_HISTORY_RETENTION_DAYS = "history_retention_days"
        const val KEY_AUTO_CHECK_ENABLED = "auto_check_update_enabled"
        const val KEY_LAST_UPDATE_CHECK_AT = "last_update_check_at"
        const val KEY_NOVEL_FONT_SIZE = "novel_font_size"
        const val KEY_NOVEL_READER_LIGHT = "novel_reader_light"
        const val KEY_NOVEL_PROGRESS_PREFIX = "novel_progress_"

        /** 小说阅读器字号范围与默认值（sp） */
        const val NOVEL_FONT_MIN = 13f
        const val NOVEL_FONT_MAX = 24f
        const val NOVEL_FONT_DEFAULT = 16f
    }
}
