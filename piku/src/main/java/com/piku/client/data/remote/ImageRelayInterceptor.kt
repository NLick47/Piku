package com.piku.client.data.remote

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer

/**
 * 受管图片上游（poipiku 图床、pixiv 图床）在中继与直连之间切换。
 *
 * 决策来自 [ImageRouteController]（按上游各持一份状态）：
 * - 判定走中继：先按线路顺序试各条中继，全挂则本次回落直连，并让下张图改走直连
 * - 判定走直连：直连失败（超时/重置）才试中继，成功就粘在中继上
 */
class ImageRelayInterceptor(
    private val controller: ImageRouteController,
    private val diagnostics: NetworkDiagnostics = NetworkDiagnostics(),
    private val runtime: NetworkRuntime = NetworkRuntime(),
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // 中转健康记忆挂在 controller 上：诊断报告要能直接读到它，不必从 OkHttpClient 里掏拦截器
        val upstream = ImageUpstream.of(request.url.host) ?: return chain.proceed(request)
        val relayHealth = controller.relayHealth(upstream)

        if (request.header(BYPASS_HEADER) != null) {
            val direct = chain.proceed(request.newBuilder().removeHeader(BYPASS_HEADER).build())
            return measured(direct, upstream, relay = false)
        }

        if (controller.useRelay(upstream)) {
            when (val outcome = tryRelays(chain, request, upstream, relayHealth)) {
                is RelayOutcome.Answered -> return outcome.response
                // 链路通但图没了（404 等）、或调用已被取消：都不是中继的错，落直连把话接完
                RelayOutcome.Rejected, RelayOutcome.Cancelled ->
                    return measured(chain.proceed(request), upstream, relay = false)
                RelayOutcome.Unreachable -> {
                    controller.markRelayFailed(upstream)
                    logDecision(upstream)
                    return measured(chain.proceed(request), upstream, relay = false)
                }
            }
        }
        return try {
            measured(chain.proceed(request), upstream, relay = false)
        } catch (e: IOException) {
            controller.markDirectFailed(upstream)
            logDecision(upstream)
            val outcome = tryRelays(chain, request, upstream, relayHealth)
            if (outcome is RelayOutcome.Answered) return outcome.response
            throw e
        }
    }

    /**
     * 给这次取图记一笔实测速率：**必须读完整包才记**，中途取消（划走/换图）不算，
     * 否则会把"用户划走了"错当成"这条路慢"。
     */
    private fun measured(response: Response, upstream: ImageUpstream, relay: Boolean): Response {
        val body = response.body ?: return response
        // 计时用单调钟：NTP 同步/手动改时不能把一次秒级下载算成分钟级
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

    /** 只在换线路时打一行，日志里能直接看到"哪个上游切到了哪边、为什么" */
    private fun logDecision(upstream: ImageUpstream) {
        val decision = controller.lastDecision(upstream) ?: return
        if (!decision.applied) return
        val target = if (decision.relay) "中继" else "直连"
        diagnostics.warn("image route ${upstream.host} -> $target｜${decision.reason}")
    }

    /** 一轮中继候选试下来的结果：只有"所有候选都连不上"才证明中继这条路坏了 */
    private sealed interface RelayOutcome {
        /** 有中继给出 2xx，直接回话 */
        data class Answered(val response: Response) : RelayOutcome

        /** 有 HTTP 应答但全非 2xx：链路是好的，图本身没了之类，与线路健康无关 */
        object Rejected : RelayOutcome

        /** 全部候选连接/超时失败：该翻走 */
        object Unreachable : RelayOutcome

        /** 调用已取消（callTimeout/用户划走）：不记失败，也不续试 */
        object Cancelled : RelayOutcome
    }

    private fun tryRelays(
        chain: Interceptor.Chain,
        request: Request,
        upstream: ImageUpstream,
        relayHealth: RelayHealth,
    ): RelayOutcome {
        var answered = false
        for (host in relayHealth.candidates()) {
            val resp = try {
                chain.proceed(rewrite(request, host, upstream))
            } catch (_: IOException) {
                // 整体已超时/取消（如 callTimeout）不算中继故障：不记失败，
                // 也不在被取消的调用上继续试下一条
                if (chain.call().isCanceled()) return RelayOutcome.Cancelled
                relayHealth.onFailure(host)
                diagnostics.warn("relay failed host=$host upstream=${upstream.host}")
                continue
            }
            answered = true
            if (resp.isSuccessful) {
                relayHealth.onSuccess(host)
                return RelayOutcome.Answered(measured(resp, upstream, relay = true))
            }
            // 非 2xx（404/502）只换这张图的下一个，不记在这条线路上
            resp.close()
        }
        return if (answered) RelayOutcome.Rejected else RelayOutcome.Unreachable
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

    companion object {
        const val BYPASS_HEADER = "X-Piku-Direct"

        val RELAY_HOSTS = listOf(
            "pic-relay.cyou",
        )
    }
}
