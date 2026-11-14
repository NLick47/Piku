package com.piku.client.core.privacy

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.piku.client.core.storage.prefs.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 应用锁：切后台即锁，回前台用系统解锁框（生物识别或锁屏凭据）解锁。
 * 开关联动最近任务遮罩（FLAG_SECURE 只在 paused 期间挂上，见 MainActivity）。
 */
class AppLockController @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var promptShowing = false

    /** 开关开着才进锁定态；开关刚打开时不立即锁，等下次切后台 */
    fun lockIfEnabled() {
        if (settingsRepository.appLockEnabled.value) {
            _locked.value = true
        }
    }

    fun unlock() {
        _locked.value = false
    }

    fun showPrompt(
        activity: FragmentActivity,
        title: CharSequence,
        negative: CharSequence,
        onSuccess: () -> Unit,
        onError: (CharSequence) -> Unit,
    ) {
        if (promptShowing) return
        promptShowing = true
        val info = with(BiometricPrompt.PromptInfo.Builder()) {
            setTitle(title)
            val authenticators = authenticators(activity)
            setAllowedAuthenticators(authenticators)
            // 带凭据回退时系统自己提供"用锁屏密码"入口，不允许再设负按钮
            if ((authenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL) == 0) {
                setNegativeButtonText(negative)
            }
            build()
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing = false
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptShowing = false
                    // 用户主动取消不算错，留在锁定态等按钮重试
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        onError(errString)
                    }
                }
            },
        )
        prompt.authenticate(info)
    }

    companion object {

        /** 设备当前能不能完成解锁：有 enrolled 生物识别或可用的系统锁屏凭据 */
        fun isAvailable(context: Context): Boolean {
            val manager = BiometricManager.from(context)
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    manager.canAuthenticate(AUTHENTICATORS_COMBINED) == BiometricManager.BIOMETRIC_SUCCESS
                // 29：无指纹但有锁屏凭据 → 走 ConfirmDeviceCredential 流程
                Build.VERSION.SDK_INT == Build.VERSION_CODES.P ->
                    manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                        BiometricManager.BIOMETRIC_SUCCESS || keyguardSecure(context)
                // 28：BiometricPrompt 不支持纯凭据，无 enrolled 指纹即不可用
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                    manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                        BiometricManager.BIOMETRIC_SUCCESS
                // 26/27：库回退到 FingerprintManager 自绘对话框
                else -> manager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
            }
        }

        /** 与 [isAvailable] 同一套分支：prompt 请求的鉴权器必须和可用性判断一致 */
        internal fun authenticators(context: Context): Int = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> AUTHENTICATORS_COMBINED
            Build.VERSION.SDK_INT == Build.VERSION_CODES.P &&
                keyguardSecure(context) &&
                BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) !=
                BiometricManager.BIOMETRIC_SUCCESS -> BiometricManager.Authenticators.DEVICE_CREDENTIAL
            else -> BiometricManager.Authenticators.BIOMETRIC_WEAK
        }

        private fun keyguardSecure(context: Context): Boolean {
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            return keyguard?.isDeviceSecure == true
        }

        private val AUTHENTICATORS_COMBINED =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
