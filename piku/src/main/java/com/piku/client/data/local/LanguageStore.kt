package com.piku.client.data.local

import android.content.SharedPreferences
import com.piku.client.domain.model.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 界面语言存储。
 * 使用 SharedPreferences（而非 DataStore）是因为 [MainActivity.attachBaseContext] 需要
 * 同步读取语言值来包裹 Locale，而 DataStore 读取是异步的。
 */
@Singleton
class LanguageStore @Inject constructor(
    private val prefs: SharedPreferences,
) {

    private val _language = MutableStateFlow(load())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
        _language.value = language
    }

    private fun load(): AppLanguage = prefs.getString(KEY_LANGUAGE, "")
        ?.let { code -> AppLanguage.entries.firstOrNull { it.code == code } }
        ?: AppLanguage.SYSTEM

    companion object {
        const val PREFS_NAME = "piku_cache"
        const val KEY_LANGUAGE = "app_language"
    }
}
