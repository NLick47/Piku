package com.piku.client.core.network.image
import com.piku.client.core.network.diag.NetworkDiagnostics
import com.piku.client.core.network.http.NetworkRuntime
import com.piku.client.core.network.http.OuterLayerOwnsRetry

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer

/**
 * 受管图片上游（poipiku 图床、pixiv 图床）在直连、中继、原生之间切换。
 *
 * 决策只有 [ImageRouteController.route] 一处；结果只有 [ImageFetchContext] 一处归并：
 * 只要拿到过 HTTP 应答，最终就交给调用方一个应答，不会被后续换路的异常覆盖。
 */
class ImageRelayInterceptor(
    private val controller: ImageRouteController,
    private val diagnostics: NetworkDiagnostics = NetworkDiagnostics(),
    private val runtime: NetworkRuntime = NetworkRuntime(),
    private val nativeTransport: NativeImageTransport? = null,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val upstream = ImageUpstream.of(request.url.host) ?: return chain.proceed(request)
        // 探测要的是直连的原始结论：原生兜底一插手，直连失败会被误报成"直连可用"
        val bypass = request.header(BYPASS_HEADER) != null
        val ctx = ImageFetchContext(upstream, runtime.monotonicNow())
        try {
            if (!bypass && !chain.call().isCanceled() && controller.route(upstream) == ImageRoute.NATIVE) {
                val epoch = controller.nativeEpoch(upstream)
                val budget = ImageCallBudget.nativeBudgetMs(runtime.monotonicNow() - ctx.startedAt)
                when (val native = nativeTransport?.fetch(request, budget)) {
                    is NativeOutcome.Ok -> {
                        if (controller.markNativeSucceeded(upstream)) logDecision(upstream)
                        return native.response
                    }
                    NativeOutcome.Failed -> {
                        if (controller.markNativeFailed(upstream, epoch)) logDecision(upstream)
                    }
                    // 对象级应答就是最终结论，不必再跑一轮常规线路
                    is NativeOutcome.Rejected -> return native.response
                    NativeOutcome.Skipped, null -> Unit
                }
            }

            val response = try {
                interceptManaged(chain, request, ctx)
            } catch (e: IOException) {
                if (chain.call().isCanceled() || bypass) throw e
                return rescue(request, ctx) ?: ctx.takeFallback() ?: throw e.unwrapBudget()
            }
            if (!bypass && worthNativeRetry(response.code) && !chain.call().isCanceled()) {
                val rescued = rescue(request, ctx)
                if (rescued != null) {
                    response.close()
                    return rescued
                }
            }
            return response
        } finally {
            ctx.release()
        }
    }

    /** 常规线路都走不通时的最后一档：取到了就把它升成正式线路，后续图不再重试死路 */
    private fun rescue(request: Request, ctx: ImageFetchContext): Response? {
        val native = nativeTransport ?: return null
        if (!controller.canTryNative(ctx.upstream)) return null
        val epoch = controller.nativeEpoch(ctx.upstream)
        val budget = ImageCallBudget.nativeBudgetMs(runtime.monotonicNow() - ctx.startedAt)
        return when (val outcome = native.fetch(request, budget)) {
            is NativeOutcome.Ok -> {
                if (controller.markNativeSucceeded(ctx.upstream)) logDecision(ctx.upstream)
                outcome.response
            }
            NativeOutcome.Failed -> {
                if (controller.markNativeFailed(ctx.upstream, epoch)) logDecision(ctx.upstream)
                null
            }
            is NativeOutcome.Rejected -> {
                ctx.offer(outcome.response)
                null
            }
            NativeOutcome.Skipped -> null
        }
    }

    /** 常规线路的预算用完了：留给原生通道，别再起一轮必然超时的连接 */
    private class ManagedBudgetExhausted(cause: IOException?) :
        IOException("管理线路预算耗尽", cause)

    /** 预算耗尽只是内部信号，交给调用方的仍是真实网络异常 */
    private fun IOException.unwrapBudget(): IOException =
        (this as? ManagedBudgetExhausted)?.cause as? IOException ?: this

    /** 常规线路的 403 可能是边缘拦截，值得用带点 SNI 再确认一次 */
    private fun worthNativeRetry(code: Int): Boolean =
        code == 403 || classifyImageStatus(code) == ImageHttpOutcome.ROUTE_TROUBLE

    private fun interceptManaged(
        chain: Interceptor.Chain,
        original: Request,
        ctx: ImageFetchContext,
    ): Response {
        val upstream = ctx.upstream
        // I/O 重试由本拦截器和 Coil 层负责：内层再试一轮会再吃满一个连接超时
        val request = original.newBuilder()
            .tag(OuterLayerOwnsRetry::class.java, OuterLayerOwnsRetry)
            .build()
        val relayHealth = controller.relayHealth(upstream)

        if (request.header(BYPASS_HEADER) != null) {
            val direct = chain.proceed(request.newBuilder().removeHeader(BYPASS_HEADER).build())
            return measured(direct, upstream, relay = false)
        }

        if (controller.baseRoute(upstream) == ImageRoute.RELAY) {
            when (val outcome = tryRelays(chain, request, upstream, relayHealth)) {
                is RelayOutcome.Answered -> return outcome.response
                is RelayOutcome.Rejected -> {
                    if (!hasRoomFor(ctx, ImageCallBudget.directWorstMs)) return outcome.response
                    ctx.offer(outcome.response)
                    return direct(chain, request, ctx, cause = null)
                }
                // 取消不是中继的错，仍按直连把话接完；下游会在已取消的 call 上立刻收场
                RelayOutcome.Cancelled ->
                    return measured(chain.proceed(request), upstream, relay = false)
                RelayOutcome.Unreachable -> {
                    controller.markRelayFailed(upstream)
                    logDecision(upstream)
                    return direct(chain, request, ctx, cause = null)
                }
            }
        }
        return try {
            direct(chain, request, ctx, cause = null)
        } catch (e: ManagedBudgetExhausted) {
            throw e
        } catch (e: IOException) {
            // 取消（用户划走 / callTimeout）不是线路故障：记下来只会把线路永久带偏
            if (chain.call().isCanceled()) throw e
            controller.markDirectFailed(upstream, isSniBlockedLike(e))
            logDecision(upstream)
            if (!hasRoomFor(ctx, ImageCallBudget.relayWorstMs)) throw ManagedBudgetExhausted(e)
            val outcome = tryRelays(chain, request, upstream, relayHealth)
            if (outcome is RelayOutcome.Answered) return outcome.response
            if (outcome is RelayOutcome.Rejected) ctx.offer(outcome.response)
            throw e
        }
    }

    private fun direct(
        chain: Interceptor.Chain,
        request: Request,
        ctx: ImageFetchContext,
        cause: IOException?,
    ): Response {
        if (!hasRoomFor(ctx, ImageCallBudget.directWorstMs)) throw ManagedBudgetExhausted(cause)
        return measured(chain.proceed(request), ctx.upstream, relay = false)
    }

    private fun hasRoomFor(ctx: ImageFetchContext, nextWorstMs: Long): Boolean =
        ImageCallBudget.hasRoom(
            elapsedMs = runtime.monotonicNow() - ctx.startedAt,
            nextWorstMs = nextWorstMs,
            keepNativeReserve = nativeTransport != null && ctx.upstream.nativeCapable &&
                controller.canTryNative(ctx.upstream),
        )

    /**
     * 给这次取图记一笔实测速率：**必须读完整包才记**，中途取消（划走/换图）不算，
     * 否则会把"用户划走了"错当成"这条路慢"。
     */
    private fun measured(response: Response, upstream: ImageUpstream, relay: Boolean): Response {
        val body = response.body ?: return response
        val startedAt = runtime.monotonicNow()
        var total = 0L
        val counting = object : ForwardingSource(body.source()) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = super.read(sink, byteCount)
                if (read == -1L) {
                    controller.recordSpeed(upstream, relay, total, runtime.monotonicNow() - startedAt)
                } else {
                    total += read
                }
                return read
            }
        }
        return response.newBuilder()
            .body(counting.buffer().asResponseBody(body.contentType(), body.contentLength()))
            .build()
    }

    /** 只在换线路时打一行，日志里能直接看到"哪个上游切到了哪条线路、为什么" */
    private fun logDecision(upstream: ImageUpstream) {
        val decision = controller.lastDecision(upstream) ?: return
        if (!decision.applied) return
        diagnostics.warn("image route ${upstream.host} -> ${decision.route.label}｜${decision.reason}")
    }

    /** 一轮中继候选试下来的结果：只有"所有候选都连不上"才证明中继这条路坏了 */
    private sealed interface RelayOutcome {
        data class Answered(val response: Response) : RelayOutcome

        data class Rejected(val response: Response) : RelayOutcome

        object Unreachable : RelayOutcome

        object Cancelled : RelayOutcome
    }

    private fun tryRelays(
        chain: Interceptor.Chain,
        request: Request,
        upstream: ImageUpstream,
        relayHealth: RelayHealth,
    ): RelayOutcome {
        var rejected: Response? = null
        for (host in relayHealth.candidates()) {
            val resp = try {
                chain.proceed(rewrite(request, host, upstream))
            } catch (_: IOException) {
                // 整体已超时/取消不算中继故障，也不在被取消的调用上继续试下一条
                if (chain.call().isCanceled()) {
                    rejected?.close()
                    return RelayOutcome.Cancelled
                }
                relayHealth.onFailure(host)
                diagnostics.warn("relay failed host=$host upstream=${upstream.host}")
                continue
            }
            if (resp.isSuccessful) {
                rejected?.close()
                relayHealth.onSuccess(host)
                return RelayOutcome.Answered(measured(resp, upstream, relay = true))
            }
            rejected?.close()
            rejected = resp
        }
        return rejected?.let { RelayOutcome.Rejected(it) } ?: RelayOutcome.Unreachable
    }

    private fun rewrite(request: Request, relayHost: String, upstream: ImageUpstream): Request =
        request.newBuilder()
            .url(
                request.url.newBuilder()
                    .host(relayHost)
                    .encodedPath(upstream.relayPath(request.url.encodedPath))
                    .build(),
            )
            .build()

    /** 一次取图的共享状态：拿到过的 HTTP 应答不能因为后续换路失败而丢掉 */
    private class ImageFetchContext(val upstream: ImageUpstream, val startedAt: Long) {
        private var fallback: Response? = null

        fun offer(response: Response) {
            if (fallback == null) fallback = response else response.close()
        }

        fun takeFallback(): Response? = fallback.also { fallback = null }

        fun release() {
            fallback?.close()
            fallback = null
        }
    }

    companion object {
        const val BYPASS_HEADER = "X-Piku-Direct"

        val RELAY_HOSTS = listOf(
            "pic-relay.cyou",
        )
    }
}
