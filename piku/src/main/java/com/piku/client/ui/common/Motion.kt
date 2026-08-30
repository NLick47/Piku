package com.piku.client.ui.common

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 系统是否关闭了动画：对应 Web 的 `prefers-reduced-motion`。
 *
 * Android 没有同名设置，最接近的是开发者选项里的「动画时长缩放」
 * （[Settings.Global.ANIMATOR_DURATION_SCALE]）。用户把它调成「关闭动画」时值为 0，
 * 此时应跳过位移、缩放、展开这类装饰性动画，只保留即时的状态切换。
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        scale == 0f
    }
}

/**
 * 按 reduced-motion 偏好折算动画时长：系统关闭动画时返回 0，
 * 效果是 `tween(0)`——目标值瞬间生效，但代码路径与正常动画完全一致，不需要分支。
 */
fun motionDuration(reducedMotion: Boolean, normalMillis: Int): Int =
    if (reducedMotion) 0 else normalMillis
