package com.piku.client.core.network.image
import com.piku.client.core.network.dns.DoHDns
import com.piku.client.core.network.http.NetworkRuntime

import android.content.SharedPreferences
import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.core.model.ImageRouteMode
import java.util.concurrent.atomic.AtomicLong

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

    /** 最近一次判定：切到哪条线路、依据什么、有没有被冷却拦下 */
    data class Decision(
        val route: ImageRoute,
        val reason: String,
        val atMillis: Long,
        val applied: Boolean,
    ) {
        val relay: Boolean get() = route == ImageRoute.RELAY
    }

    private class Route(health: RelayHealth, relay: Boolean, speed: RouteSpeed) {
        val health: RelayHealth = health
        val speed: RouteSpeed = speed

        /** 直连/中继之间的基底选择：原生通道只是叠在它上面的一层，不改这里的结论 */
        @Volatile
        var relay: Boolean = relay

        /** 直连是否已被判定不可用：探测说不可用或直连请求失败过，只有探测说可用才解除 */
        @Volatile
        var directDown: Boolean = false

        /** 判死时刻：线路决策只认这之后的新测速样本，档位估算不受影响 */
        @Volatile
        var directDownAt: Long = Long.MIN_VALUE

        /** 原生通道的粘性截止（单调钟）；0 表示不在原生上 */
        @Volatile
        var nativeUntil: Long = 0L

        /** 原生通道失败后的冷却截止：期间不再拿它兜底，免得每张图都白等一轮 */
        @Volatile
        var nativeBlockUntil: Long = 0L

        /** 原生线路世代：迟到的失败不能覆盖之后建立的成功 */
        val nativeEpoch = AtomicLong(0L)

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

    /** 这次取图是否走中继——与 [route] 同一口径，原生活跃时它不算中继 */
    fun useRelay(upstream: ImageUpstream): Boolean = route(upstream) == ImageRoute.RELAY

    /** 基底线路（不含原生层）：原生被并发、预算或库缺失跳过时，常规路径按它走 */
    fun baseRoute(upstream: ImageUpstream): ImageRoute =
        if (resolveRelay(stateOf(upstream))) ImageRoute.RELAY else ImageRoute.DIRECT

    /** 这次取图实际该走哪条线路：原生生效期内它优先于直连/中继 */
    fun route(upstream: ImageUpstream): ImageRoute {
        val state = stateOf(upstream)
        return when {
            isNativeActive(state, runtime.monotonicNow()) -> ImageRoute.NATIVE
            resolveRelay(state) -> ImageRoute.RELAY
            else -> ImageRoute.DIRECT
        }
    }

    /** 原生通道生效中：粘性未过期，且不在失败后的静默期里 */
    private fun isNativeActive(state: Route, now: Long): Boolean =
        now < state.nativeUntil && now >= state.nativeBlockUntil

    /** 基底线路的唯一出口：判死通过给测速证据设时间下限生效，不与测速各存一套结论 */
    private fun resolveRelay(state: Route): Boolean = when (mode) {
        ImageRouteMode.DIRECT -> false
        ImageRouteMode.RELAY -> true
        ImageRouteMode.AUTO -> byMeasuredSpeed(state)
    }

    /** 原生通道刚失败过就先别碰它：每试它一次都要吃满一个地址的超时 */
    fun canTryNative(upstream: ImageUpstream): Boolean =
        runtime.monotonicNow() >= stateOf(upstream).nativeBlockUntil

    fun nativeEpoch(upstream: ImageUpstream): Long = stateOf(upstream).nativeEpoch.get()

    /**
     * 原生通道真取到图：两条常规线路此时都不通，后续的图直接走它。
     * @return 是否刚从别的线路切过来——只有切换才值得打一行日志
     */
    fun markNativeSucceeded(upstream: ImageUpstream): Boolean {
        val state = stateOf(upstream)
        val now = runtime.monotonicNow()
        val entered = !isNativeActive(state, now)
        state.nativeUntil = now + NATIVE_TTL_MS
        state.nativeBlockUntil = 0L
        state.nativeEpoch.incrementAndGet()
        state.lastDecision = Decision(ImageRoute.NATIVE, "原生通道取图成功", runtime.now(), applied = true)
        return entered
    }

    /** [epoch] 是请求发起时的世代；不匹配说明这是迟到的失败，不能改当前线路 */
    fun markNativeFailed(upstream: ImageUpstream, epoch: Long? = null): Boolean {
        val state = stateOf(upstream)
        if (epoch != null && epoch != state.nativeEpoch.get()) return false
        state.nativeUntil = 0L
        state.nativeBlockUntil = runtime.monotonicNow() + NATIVE_BLOCK_MS
        state.nativeEpoch.incrementAndGet()
        val back = if (resolveRelay(state)) ImageRoute.RELAY else ImageRoute.DIRECT
        state.lastDecision = Decision(back, "原生通道失败，回到${back.label}", runtime.now(), applied = true)
        return true
    }

    /** 判死之后的旧样本不能推翻判死；新证据仍可，没证据就沿用粘住的结论 */
    private fun byMeasuredSpeed(state: Route): Boolean {
        val direct = state.speed.rate(relay = false, since = state.directDownAt) ?: return state.relay
        if (direct >= RouteSpeed.FAST_ENOUGH_BYTES_PER_SEC) return false
        val relay = state.speed.rate(relay = true)
        return if (relay != null) relay > direct else state.relay
    }

    /** 每次取图完成后报一次实测：字节数 + 耗时，用来算这条路的速率 */
    fun recordSpeed(upstream: ImageUpstream, relay: Boolean, bytes: Long, elapsedMs: Long) {
        stateOf(upstream).speed.record(relay, bytes, elapsedMs)
    }

    fun worthFullImageInline(upstream: ImageUpstream): Boolean {
        // 原生通道没有历史测速样本，但它此时是唯一通的线路：降级只会剩下糊图
        if (route(upstream) == ImageRoute.NATIVE) return true
        val estimate = stateOf(upstream).speed.estimatedMs(
            bytes = RouteSpeed.FULL_IMAGE_BYTES_ESTIMATE,
            relay = useRelay(upstream),
        ) ?: return false
        return estimate <= RouteSpeed.FULL_IMAGE_BUDGET_MS
    }

    internal fun speed(upstream: ImageUpstream): RouteSpeed = stateOf(upstream).speed

    internal fun relayHealth(upstream: ImageUpstream): RelayHealth = stateOf(upstream).health

    fun lastDecision(upstream: ImageUpstream): Decision? = stateOf(upstream).lastDecision

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
    fun applyProbe(upstream: ImageUpstream, directOk: Boolean) {
        // 探测是一次新鲜测量：之前粘在原生通道上的结论作废，手动模式同样如此
        val state = stateOf(upstream)
        state.nativeUntil = 0L
        state.nativeBlockUntil = 0L
        state.nativeEpoch.incrementAndGet()
        ifAuto {
            if (!directOk) {
                state.directDown = true
                state.directDownAt = runtime.monotonicNow()
                // 判死要立刻止损；冷却是防失败信号来回翻转，不该拦一次全新的探测
                setRelay(upstream, true, "探测：直连不可用", respectCooldown = false)
            } else {
                state.directDown = false
                state.directDownAt = Long.MIN_VALUE
                setRelay(upstream, false, "探测：直连可用")
            }
        }
    }

    fun markDirectFailed(upstream: ImageUpstream) = ifAuto {
        val state = stateOf(upstream)
        state.directDown = true
        state.directDownAt = runtime.monotonicNow()
        setRelay(upstream, true, "直连请求失败")
    }

    /**
     * 中转不可达。直连同时也已被判定不通时，两条常规线路就都废了：
     * 这时先按"试一次原生"处理（4s 预算），比每张图再各白等一轮便宜得多。
     */
    fun markRelayFailed(upstream: ImageUpstream) = ifAuto {
        val state = stateOf(upstream)
        setRelay(upstream, false, "中转请求失败")
        // pixiv 没有原生通道：arm 了试探也永远 Skipped，还会让详情图误判成"原生是唯一通路"强制全尺寸
        if (state.directDown && upstream.nativeCapable && canTryNative(upstream)) {
            state.nativeUntil = maxOf(state.nativeUntil, runtime.monotonicNow() + NATIVE_TRIAL_MS)
            state.lastDecision = Decision(
                ImageRoute.NATIVE,
                "直连与中转均不通，改试原生通道",
                runtime.now(),
                applied = true,
            )
        }
    }

    private inline fun ifAuto(block: () -> Unit) {
        if (mode == ImageRouteMode.AUTO) block()
    }

    private fun stateOf(upstream: ImageUpstream): Route = routes.getValue(upstream)

    private fun setRelay(
        upstream: ImageUpstream,
        relay: Boolean,
        reason: String,
        respectCooldown: Boolean = true,
    ) {
        val state = stateOf(upstream)
        if (state.relay == relay) {
            state.lastDecision = Decision(kindOf(relay), "$reason（已是该状态）", runtime.now(), applied = true)
            return
        }
        // 直连已判定不可用：切回去也必然失败，只是多打一轮连接和解析失败记录，留在中转上
        if (!relay && state.directDown) {
            state.lastDecision = Decision(
                route = ImageRoute.RELAY,
                reason = "$reason（直连已判定不可用，不切回）",
                atMillis = runtime.now(),
                applied = true,
            )
            return
        }
        val now = runtime.monotonicNow()
        // 直连与中转同时全挂时，每请求都来回翻转会反复打满两种链路的连接数，
        // 也给 DoHDns 制造无谓的失败记录。冷却期内不翻，让状态粘住、尽快失败。
        if (respectCooldown && now - state.lastFlipAt < FLIP_COOLDOWN_MS) {
            state.lastDecision = Decision(kindOf(relay), "$reason（冷却中，未翻转）", runtime.now(), applied = false)
            return
        }
        state.relay = relay
        state.lastFlipAt = now
        prefs.edit().putBoolean(keyOf(upstream), relay).apply()
        state.lastDecision = Decision(kindOf(relay), reason, runtime.now(), applied = true)
    }

    private fun kindOf(relay: Boolean): ImageRoute = if (relay) ImageRoute.RELAY else ImageRoute.DIRECT

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

        /** 原生通道成功后的粘性时长：期间不再为每张图重试两条已知不通的线路 */
        const val NATIVE_TTL_MS = 120_000L

        /** 静默只覆盖同一批并发请求：原生单次预算上限 6s，封 60s 会把用户钉死在两条已判定不通的常规线路上 */
        const val NATIVE_BLOCK_MS = 5_000L

        /** 两条常规线路都失败时，先按"试原生"处理一段；真取到图会延长到 [NATIVE_TTL_MS] */
        const val NATIVE_TRIAL_MS = 30_000L
    }
}
