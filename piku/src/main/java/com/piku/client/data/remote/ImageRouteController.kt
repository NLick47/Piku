package com.piku.client.data.remote

import android.content.SharedPreferences
import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.ImageRouteMode

class ImageRouteController(
    private val settings: SettingsRepository,
    private val prefs: SharedPreferences,
    private val runtime: NetworkRuntime = NetworkRuntime(),
    relayHosts: List<String> = ImageRelayInterceptor.RELAY_HOSTS,
) {

    /** 中转线路健康（决策用，也供诊断展示） */
    internal val relayHealth = RelayHealth(relayHosts, runtime.now)

    /** 最近一次判定：切到哪边、依据什么、有没有被冷却拦下 */
    data class Decision(val relay: Boolean, val reason: String, val atMillis: Long, val applied: Boolean)

    @Volatile
    var lastDecision: Decision? = null
        private set

    @Volatile
    var lastProbe: ImageProbeResult? = null
        private set

    @Volatile
    private var autoRelay: Boolean = prefs.getBoolean(KEY_AUTO_RELAY, false)

    /** 直连是否已被判定不可用：探测说不可用或直连请求失败过，只有探测说可用才解除 */
    @Volatile
    private var directDown: Boolean = false

    @Volatile
    private var lastFlipAt: Long = 0L

    val mode: ImageRouteMode
        get() = settings.imageRouteMode.value

    val useRelay: Boolean
        get() = when (mode) {
            ImageRouteMode.DIRECT -> false
            ImageRouteMode.RELAY -> true
            ImageRouteMode.AUTO -> autoRelay
        }

    /** 仅 AUTO 模式需要启动探测；手动选了直连/中转的用户不必浪费这次请求 */
    fun shouldRunProbe(): Boolean = mode == ImageRouteMode.AUTO

    /** 启动或重新探测的结论：记下详情供诊断，并按结论调整自动选择 */
    fun applyProbe(result: ImageProbeResult) {
        lastProbe = result
        applyProbe(result.ok)
    }

    /** 启动探测结论：直连是否可用（仅 AUTO 下影响结果） */
    fun applyProbe(directOk: Boolean) = ifAuto {
        directDown = !directOk
        setAuto(!directOk, if (directOk) "启动探测：直连可用" else "启动探测：直连不可用")
    }

    fun markDirectFailed() = ifAuto {
        directDown = true
        setAuto(true, "直连请求失败")
    }

    fun markRelayFailed() = ifAuto { setAuto(false, "中转请求失败") }

    private inline fun ifAuto(block: () -> Unit) {
        if (mode == ImageRouteMode.AUTO) block()
    }

    private fun setAuto(relay: Boolean, reason: String) {
        if (autoRelay == relay) {
            lastDecision = Decision(relay, "$reason（已是该状态）", runtime.now(), applied = true)
            return
        }
        // 直连已判定不可用：切回去也必然失败，只是多打一轮连接和解析失败记录，留在中转上
        if (!relay && directDown) {
            lastDecision = Decision(
                relay = true,
                reason = "$reason（直连已判定不可用，不切回）",
                atMillis = runtime.now(),
                applied = true,
            )
            return
        }
        val now = runtime.now()
        // 直连与中转同时全挂时，每请求都来回翻转会反复打满两种链路的连接数，
        // 也给 DoHDns 制造无谓的失败记录。冷却期内不翻，让状态粘住、尽快失败。
        if (now - lastFlipAt < FLIP_COOLDOWN_MS) {
            lastDecision = Decision(relay, "$reason（冷却中，未翻转）", now, applied = false)
            return
        }
        autoRelay = relay
        lastFlipAt = now
        prefs.edit().putBoolean(KEY_AUTO_RELAY, relay).apply()
        lastDecision = Decision(relay, reason, now, applied = true)
    }

    private companion object {
        const val KEY_AUTO_RELAY = "image_route_auto_relay"
        const val FLIP_COOLDOWN_MS = 20_000L
    }
}
