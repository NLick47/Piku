package com.piku.client

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Log
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.piku.client.core.storage.media.ImageShareHelper
import com.piku.client.core.storage.prefs.LanguageStore
import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.core.model.AppLanguage
import com.piku.client.core.model.ThemeMode
import com.piku.client.core.privacy.AppLockController
import com.piku.client.core.privacy.AppLockOverlay
import com.piku.client.app.navigation.AppNavHost
import com.piku.client.core.ui.theme.PoipikuTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var imageShareHelper: ImageShareHelper

    @Inject
    lateinit var appLockController: AppLockController

    private val pendingDeepLink = mutableStateOf<String?>(null)

    /** 经历过一次 onStop：再 onStart 就是回前台而非冷启动 */
    private var backgrounded = false

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
        // 冷启动进锁定态；旋转等配置变更重建不锁。锁前先确认设备可解锁，否则永远进不去
        if (savedInstanceState == null || !isChangingConfigurations) {
            lockIfAvailable()
        }
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
                // 锁定遮罩盖在 NavHost 上而不换出组合树：保住页面状态
                val locked by appLockController.locked.collectAsState()
                Box {
                    AppNavHost(
                        deepLink = pendingDeepLink.value,
                        onDeepLinkConsumed = { pendingDeepLink.value = null },
                    )
                    if (locked) {
                        AppLockOverlay(onUnlock = ::requestUnlock)
                    }
                }
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

    override fun onStart() {
        super.onStart()
        if (backgrounded) {
            backgrounded = false
            lockIfAvailable()
        }
    }

    override fun onResume() {
        super.onResume()
        // 回前台即放行截屏；遮罩只作用于后台期间的最近任务缩略图
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        lifecycleScope.launch { imageShareHelper.cleanupCache() }
    }

    override fun onPause() {
        super.onPause()
        if (settingsRepository.appLockEnabled.value) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onStop() {
        super.onStop()
        backgrounded = true
    }

    private fun lockIfAvailable() {
        if (settingsRepository.appLockEnabled.value && AppLockController.isAvailable(this)) {
            appLockController.lockIfEnabled()
        }
    }

    private fun requestUnlock() {
        appLockController.showPrompt(
            this,
            title = getString(R.string.app_lock_prompt_title),
            negative = getString(R.string.app_lock_unlock),
            onSuccess = { appLockController.unlock() },
            // 硬件/超时类错误留在锁定态，遮罩上的按钮可重试
            onError = { _ -> },
        )
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
