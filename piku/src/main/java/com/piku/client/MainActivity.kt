package com.piku.client

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Log
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.piku.client.data.local.ImageShareHelper
import com.piku.client.data.local.LanguageStore
import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.AppLanguage
import com.piku.client.domain.model.ThemeMode
import com.piku.client.ui.navigation.AppNavHost
import com.piku.client.ui.theme.PoipikuTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var imageShareHelper: ImageShareHelper

    private val pendingDeepLink = mutableStateOf<String?>(null)

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
        if (savedInstanceState == null) {
            pendingDeepLink.value = intent?.dataString
            if (pendingDeepLink.value != null) {
                Log.d(TAG, "cold start deep link: ${pendingDeepLink.value}")
            }
        }
        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            val dark = themeMode.isDark(systemDark)
            PoipikuTheme(darkTheme = dark) {
                SystemBarIconsFollowAppTheme(window = window, dark = dark)
                AppNavHost(
                    deepLink = pendingDeepLink.value,
                    onDeepLinkConsumed = { pendingDeepLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let {
            Log.d(TAG, "new intent deep link: $it")
            pendingDeepLink.value = it
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { imageShareHelper.cleanupCache() }
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

    private companion object {
        const val TAG = "PikuDiag"
    }
}

@Composable
private fun SystemBarIconsFollowAppTheme(window: Window, dark: Boolean) {
    val view = LocalView.current
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
