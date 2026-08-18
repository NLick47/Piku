package com.piku.client

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.piku.client.data.local.LanguageStore
import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.AppLanguage
import com.piku.client.domain.model.ThemeMode
import com.piku.client.ui.navigation.AppNavHost
import com.piku.client.ui.theme.PoipikuTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences(LanguageStore.PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(LanguageStore.KEY_LANGUAGE, null)
        val supportedCode = code?.takeIf { savedCode ->
            savedCode.isNotBlank() && AppLanguage.entries.any { it.code == savedCode }
        }
        super.attachBaseContext(supportedCode?.let { newBase.withLocale(it) } ?: newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            PoipikuTheme(darkTheme = themeMode.isDark(systemDark)) {
                AppNavHost()
            }
        }
    }

    private fun Context.withLocale(code: String): Context {
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.setLocale(locale)
        }
        return createConfigurationContext(config)
    }
}
