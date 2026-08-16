package com.piku.client.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.piku.client.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val showAdultContent: Flow<Boolean> = dataStore.data.map { it[KEY_SHOW_ADULT_CONTENT] ?: false }

    suspend fun setShowAdultContent(value: Boolean) {
        dataStore.edit { it[KEY_SHOW_ADULT_CONTENT] = value }
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE]
            ?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    /** 浏览记录保留天数，0 表示永久保留 */
    val historyRetentionDays: Flow<Int> = dataStore.data.map { it[KEY_HISTORY_RETENTION_DAYS] ?: 0 }

    suspend fun setHistoryRetentionDays(days: Int) {
        dataStore.edit { it[KEY_HISTORY_RETENTION_DAYS] = days }
    }

    private companion object {
        val KEY_SHOW_ADULT_CONTENT = booleanPreferencesKey("show_adult_content")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_HISTORY_RETENTION_DAYS = intPreferencesKey("history_retention_days")
    }
}
