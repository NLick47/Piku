package com.piku.client.core.network.image

import android.util.Log
import com.piku.client.core.network.dns.DoHDns
import com.piku.client.core.network.http.NetworkRuntime
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.Semaphore

class NativeImageSpec(
    val url: String,
    val headers: List<Pair<String, String>>,
    val ip: String,
    val timeoutMs: Int,
)

class NativeImageResponse(
    val status: Int,
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
)

/** 原生通道一次取图的结果：只有 [Failed] 才算线路故障，其余都不该影响线路选择 */
sealed interface NativeOutcome {
    data class Ok(val response: Response) : NativeOutcome

    /** 不适用（非 poipiku 图、非 GET）、并发已满、原生库缺失 */
    object Skipped : NativeOutcome

    /**
     * 通道通了，但对端给了确定的应答（4xx 对象缺失、3xx 无 Location）。
     * 这是这张图的事，不是线路健康问题——不能拿它去判线路死刑。
     */
    data class Rejected(val response: Response) : NativeOutcome

    /** 所有地址都没连上 / 没回话：线路故障 */
    object Failed : NativeOutcome
}

/**
 * 带点 SNI 的原生 TLS 取图通道。
 *
 * 这里只负责"怎么取"：地址轮换、单地址预算、并发上限。
 * "要不要走"由 [ImageRouteController] 决定，避免两个地方各自记一套线路状态。
 */
class NativeImageTransport(
    private val userAgent: String,
    private val runtime: NetworkRuntime = NetworkRuntime(),
    private val fetchNative: (NativeImageSpec) -> NativeImageResponse,
) {

    private val inFlight = Semaphore(MAX_IN_FLIGHT)

    fun fetch(request: Request, budgetMs: Int): NativeOutcome {
        if (budgetMs <= 0) return NativeOutcome.Skipped
        val url = request.url
        val upstream = ImageUpstream.of(url.host) ?: return NativeOutcome.Skipped
        if (!upstream.nativeCapable || request.method != "GET") return NativeOutcome.Skipped
        val addresses = DoHDns.STATIC_ADDRESSES[upstream.host] ?: return NativeOutcome.Skipped
        if (!inFlight.tryAcquire()) return NativeOutcome.Skipped
        try {
            return attempt(request, addresses, budgetMs)
        } finally {
            inFlight.release()
        }
    }

    private fun attempt(request: Request, addresses: List<String>, budgetMs: Int): NativeOutcome {
        val url = request.url
        val dotted = url.newBuilder().host("${url.host}.").build().toString()
        val headers = listOf(
            "user-agent" to userAgent,
            "accept" to "image/*,*/*;q=0.8",
            // 与常规线路的 RefererInterceptor 同口径：poipiku CDN 认本站地址，缺 Referer 可能吃边缘防盗链
            "referer" to request.url.newBuilder().query(null).fragment(null).build().toString(),
        )
        val deadline = runtime.monotonicNow() + budgetMs
        var lastError: IOException? = null
        var attempted = false
        addresses.forEach { ip ->
            val remainMs = (deadline - runtime.monotonicNow()).toInt()
            if (remainMs < MIN_PER_IP_MS) return@forEach
            // 可用性优先：每个地址独立给足一次尝试，失败才轮到下一个；
            // 不为后面的地址预留切片，否则地址一多每个都只剩 1s
            val sliceMs = remainMs.coerceAtMost(PER_IP_TIMEOUT_MS)
            attempted = true
            val result = try {
                fetchNative(NativeImageSpec(dotted, headers, ip, sliceMs))
            } catch (e: IOException) {
                lastError = e
                return@forEach
            } catch (e: LinkageError) {
                Log.w(TAG, "native library unavailable: ${e.message}")
                return NativeOutcome.Skipped
            } catch (e: RuntimeException) {
                // 帧解析坏掉这类传输层故障：通道坏了，不是线路坏了，别去碰路由状态
                Log.w(TAG, "native transport malfunction: ${e.message}")
                return NativeOutcome.Skipped
            }
            when (classifyImageStatus(result.status)) {
                ImageHttpOutcome.SUCCESS -> {
                    Log.d(TAG, "native sni $ip -> ${result.status} ${result.body.size}B")
                    return NativeOutcome.Ok(result.toOkHttp(request))
                }
                ImageHttpOutcome.OBJECT_REJECTED, ImageHttpOutcome.REDIRECT -> {
                    Log.d(TAG, "native sni $ip -> ${result.status}（对象级应答，不记线路故障）")
                    return NativeOutcome.Rejected(result.toOkHttp(request))
                }
                ImageHttpOutcome.ROUTE_TROUBLE -> lastError = IOException("HTTP ${result.status}")
            }
        }
        if (!attempted) {
            Log.d(TAG, "native sni skipped: 预算不足")
            return NativeOutcome.Skipped
        }
        Log.w(TAG, "native sni failed: ${lastError?.message}")
        return NativeOutcome.Failed
    }

    private fun NativeImageResponse.toOkHttp(request: Request): Response {
        val type = headers
            .firstOrNull { it.first.equals("content-type", ignoreCase = true) }
            ?.second
            ?.toMediaTypeOrNull()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(status)
            .message("native sni")
            .body(body.toResponseBody(type))
            .apply {
                headers.forEach { (name, value) ->
                    if (!name.startsWith(":") && !name.equals("content-type", ignoreCase = true)) {
                        addHeader(name, value)
                    }
                }
            }
            .build()
    }

    private companion object {
        const val TAG = "PikuNativeSni"

        /** 与原生侧 `timeout_ms.max(1000)` 对齐，否则更短的切片会被悄悄拉长 */
        const val MIN_PER_IP_MS = 1_000

        /** 单个地址给 3s，够慢网把一张 _640 读完；失败立即换下一个 */
        const val PER_IP_TIMEOUT_MS = 3_000

        /** 与 OkHttp 默认的 maxRequestsPerHost 对齐：原生已是正式线路时不该被自己卡住 */
        const val MAX_IN_FLIGHT = 5
    }
}
