package com.piku.client.data.remote

import android.content.SharedPreferences
import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.ImageRouteMode

class ImageRouteController(
    private val settings: SettingsRepository,
    private val prefs: SharedPreferences,
) {

    @Volatile
    private var autoRelay: Boolean = prefs.getBoolean(KEY_AUTO_RELAY, false)

    @Volatile
    private var lastFlipAt: Long = 0L

    val useRelay: Boolean
        get() = when (settings.imageRouteMode.value) {
            ImageRouteMode.DIRECT -> false
            ImageRouteMode.RELAY -> true
            ImageRouteMode.AUTO -> autoRelay
        }

    /** 仅 AUTO 模式需要启动探测；手动选了直连/中转的用户不必浪费这次请求 */
    fun shouldRunProbe(): Boolean = settings.imageRouteMode.value == ImageRouteMode.AUTO

    /** 启动探测结论：直连是否可用（仅 AUTO 下影响结果） */
    fun applyProbe(directOk: Boolean) = ifAuto { setAuto(!directOk) }

    fun markDirectFailed() = ifAuto { setAuto(true) }

    fun markRelayFailed() = ifAuto { setAuto(false) }

    private inline fun ifAuto(block: () -> Unit) {
        if (settings.imageRouteMode.value == ImageRouteMode.AUTO) block()
    }

    private fun setAuto(relay: Boolean) {
        if (autoRelay == relay) return
        // 直连与中转同时全挂时，每请求都来回翻转会反复打满两种链路的连接数，
        // 也给 DoHDns 制造无谓的失败记录。冷却期内不翻，让状态粘住、尽快失败。
        val now = System.currentTimeMillis()
        if (now - lastFlipAt < FLIP_COOLDOWN_MS) return
        autoRelay = relay
        lastFlipAt = now
        prefs.edit().putBoolean(KEY_AUTO_RELAY, relay).apply()
    }

    private companion object {
        const val KEY_AUTO_RELAY = "image_route_auto_relay"
        const val FLIP_COOLDOWN_MS = 20_000L
    }
}
