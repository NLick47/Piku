package com.piku.client.data.remote

import android.content.SharedPreferences
import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.ImageRouteMode

/**
 * 图片线路决策：直连还是中继。
 *
 * poipiku 与 pixiv 各有一套状态——同一条线路上直连 `cdn.poipiku.com` 是好的，
 * 而直连 `i.pximg.net` 只有几十 KB/s（大图必然撞超时），默认值本来就该不同；
 * 中继回源失败也不该把另一条上游一起带偏。
 */
class ImageRouteController(
    private val settings: SettingsRepository,
    private val prefs: SharedPreferences,
    private val runtime: NetworkRuntime = NetworkRuntime(),
    relayHosts: List<String> = ImageRelayInterceptor.RELAY_HOSTS,
) {

    /** 最近一次判定：切到哪边、依据什么、有没有被冷却拦下 */
    data class Decision(val relay: Boolean, val reason: String, val atMillis: Long, val applied: Boolean)

    private class Route(health: RelayHealth, relay: Boolean, speed: RouteSpeed) {
        val health: RelayHealth = health
        val speed: RouteSpeed = speed

        @Volatile
        var relay: Boolean = relay

        /** 直连是否已被判定不可用：探测说不可用或直连请求失败过，只有探测说可用才解除 */
        @Volatile
        var directDown: Boolean = false

        @Volatile
        var lastFlipAt: Long = 0L

        @Volatile
        var lastDecision: Decision? = null
    }

    private val routes: Map<ImageUpstream, Route> = ImageUpstream.entries.associateWith { upstream ->
        Route(
            health = RelayHealth(relayHosts, runtime.now),
            relay = persistedRelay(upstream),
            // 测速样本的时效窗口用单调钟：改时间不该把"刚测的"变成"过期"
            speed = RouteSpeed(runtime.monotonicNow),
        )
    }

    @Volatile
    var lastProbe: ImageProbeResult? = null
        private set

    val mode: ImageRouteMode
        get() = settings.imageRouteMode.value

    fun useRelay(upstream: ImageUpstream): Boolean = when (mode) {
        ImageRouteMode.DIRECT -> false
        ImageRouteMode.RELAY -> true
        ImageRouteMode.AUTO -> autoRoute(upstream)
    }

    /**
     * AUTO 下的选择：**实测速率优先**。
     * - 直连实测够快 → 直连（境外/好网络上不该花中继）
     * - 直连实测不达标、中继更快 → 中继
     * - 没测出直连速率 → 用粘住的结论（首次是直连，失败后才翻到中继）
     */
    private fun autoRoute(upstream: ImageUpstream): Boolean {
        val route = route(upstream)
        val direct = route.speed.rate(relay = false) ?: return route.relay
        if (direct >= RouteSpeed.FAST_ENOUGH_BYTES_PER_SEC) return false
        val relay = route.speed.rate(relay = true)
        return if (relay != null) relay > direct else route.relay
    }

    /** 每次取图完成后报一次实测：字节数 + 耗时，用来算这条路的速率 */
    fun recordSpeed(upstream: ImageUpstream, relay: Boolean, bytes: Long, elapsedMs: Long) {
        route(upstream).speed.record(relay, bytes, elapsedMs)
    }

    fun worthFullImageInline(upstream: ImageUpstream): Boolean {
        val estimate = route(upstream).speed.estimatedMs(
            bytes = RouteSpeed.FULL_IMAGE_BYTES_ESTIMATE,
            relay = useRelay(upstream),
        ) ?: return false
        return estimate <= RouteSpeed.FULL_IMAGE_BUDGET_MS
    }

    internal fun speed(upstream: ImageUpstream): RouteSpeed = route(upstream).speed

    internal fun relayHealth(upstream: ImageUpstream): RelayHealth = route(upstream).health

    fun lastDecision(upstream: ImageUpstream): Decision? = route(upstream).lastDecision

    /** 仅 AUTO 模式需要启动探测；手动选了直连/中转的用户不必浪费这次请求 */
    fun shouldRunProbe(): Boolean = mode == ImageRouteMode.AUTO

    /** 启动或重新探测的结论：记下详情供诊断，并按结论调整自动选择 */
    fun applyProbe(result: ImageProbeResult) {
        lastProbe = result
        applyProbe(result.ok)
    }

    /** 启动探测探的是 poipiku 那条直连，只影响这条上游 */
    fun applyProbe(directOk: Boolean) = applyProbe(ImageUpstream.POIPIKU, directOk)

    /** 探测结论落到指定上游：直连可用就切回直连（中转省着点用），不可用才上中转 */
    fun applyProbe(upstream: ImageUpstream, directOk: Boolean) = ifAuto {
        route(upstream).directDown = !directOk
        setRelay(upstream, !directOk, if (directOk) "探测：直连可用" else "探测：直连不可用")
    }

    fun markDirectFailed(upstream: ImageUpstream) = ifAuto {
        route(upstream).directDown = true
        setRelay(upstream, true, "直连请求失败")
    }

    fun markRelayFailed(upstream: ImageUpstream) = ifAuto { setRelay(upstream, false, "中转请求失败") }

    private inline fun ifAuto(block: () -> Unit) {
        if (mode == ImageRouteMode.AUTO) block()
    }

    private fun route(upstream: ImageUpstream): Route = routes.getValue(upstream)

    private fun setRelay(upstream: ImageUpstream, relay: Boolean, reason: String) {
        val route = route(upstream)
        if (route.relay == relay) {
            route.lastDecision = Decision(relay, "$reason（已是该状态）", runtime.now(), applied = true)
            return
        }
        // 直连已判定不可用：切回去也必然失败，只是多打一轮连接和解析失败记录，留在中转上
        if (!relay && route.directDown) {
            route.lastDecision = Decision(
                relay = true,
                reason = "$reason（直连已判定不可用，不切回）",
                atMillis = runtime.now(),
                applied = true,
            )
            return
        }
        val now = runtime.monotonicNow()
        // 直连与中转同时全挂时，每请求都来回翻转会反复打满两种链路的连接数，
        // 也给 DoHDns 制造无谓的失败记录。冷却期内不翻，让状态粘住、尽快失败。
        if (now - route.lastFlipAt < FLIP_COOLDOWN_MS) {
            route.lastDecision = Decision(relay, "$reason（冷却中，未翻转）", runtime.now(), applied = false)
            return
        }
        route.relay = relay
        route.lastFlipAt = now
        prefs.edit().putBoolean(keyOf(upstream), relay).apply()
        route.lastDecision = Decision(relay, reason, runtime.now(), applied = true)
    }

    /**
     * 两条上游都是"先直连"：能直连的网络（境外、开了梯子）一分中继资源都不该花。
     * 直连真的失败（大图撞超时）才切中继，并把结论持久化，下次进来直接用中继；
     * 想再验证直连可以走诊断页的重新探测。
     */
    private fun persistedRelay(upstream: ImageUpstream): Boolean = when (upstream) {
        ImageUpstream.PIXIV -> prefs.getBoolean(KEY_AUTO_RELAY_PIXIV, false)
        ImageUpstream.POIPIKU -> prefs.getBoolean(KEY_AUTO_RELAY, false)
    }

    private fun keyOf(upstream: ImageUpstream): String = when (upstream) {
        ImageUpstream.PIXIV -> KEY_AUTO_RELAY_PIXIV
        ImageUpstream.POIPIKU -> KEY_AUTO_RELAY
    }

    private companion object {
        const val KEY_AUTO_RELAY = "image_route_auto_relay"
        const val KEY_AUTO_RELAY_PIXIV = "image_route_auto_relay_pixiv"
        const val FLIP_COOLDOWN_MS = 20_000L
    }
}
