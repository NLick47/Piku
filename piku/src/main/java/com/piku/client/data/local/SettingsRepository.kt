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

    private companion object {
        const val KEY_SHOW_ADULT_CONTENT = "show_adult_content"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_HISTORY_RETENTION_DAYS = "history_retention_days"
    }
}
