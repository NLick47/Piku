package com.piku.client.data.remote

import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** 一次图片线路探测的结果 */
data class ImageProbeResult(
    val ok: Boolean,
    val elapsedMs: Long,
    val statusCode: Int?,
    val error: String?,
    val atMillis: Long,
    /** 实际收到的字节数：直连"能连上但传不完"时靠它区分"通"和"可用" */
    val bytes: Long = 0,
)

class ImageRouteProbe(
    private val client: Call.Factory,
    private val controller: ImageRouteController,
    private val runtime: NetworkRuntime = NetworkRuntime(),
) {

    /**
     * 启动时的自动探测：手动选了直连/中转的用户跳过，省一次请求。
     * 只探 poipiku 那条（小图标，几十毫秒）；pixiv 的大图探测要十几秒，
     * 不放启动路径上——那边靠真实请求的失败信号切中继，诊断页里也能手动重探。
     */
    fun run() {
        if (!controller.shouldRunProbe()) return
        val upstream = ImageUpstream.POIPIKU
        val result = probe(upstream, relayHost = null)
        verdict(upstream, result)?.let { controller.applyProbe(upstream, it) }
    }

    /**
     * 一次探测能得出什么结论：
     * - 抛异常 → 直连不可用
     * - 2xx → 直连可用
     * - 小图探测拿到别的响应（404 等）→ 链路是通的，仍算直连可用（探测图被删不该把用户推去中继）
     * - **大图探测拿到非 2xx → 判不了**：404 体量是零，证明不了"大图传得完"，保持现状
     */
    fun verdict(upstream: ImageUpstream, result: ImageProbeResult): Boolean? = when {
        result.error != null -> false
        result.statusCode in 200..299 -> true
        upstream.probeWithBody -> null
        else -> true
    }

    /**
     * 探测某条上游的直连或某条中转线路。
     *
     * 走直连时带绕过头（否则测的是中转）；pixiv 的探测要真读 body，
     * 因为它的结论取决于**大图传不传得完**，只看响应头会永远判"可用"。
     */
    fun probe(upstream: ImageUpstream, relayHost: String?): ImageProbeResult {
        val request = Request.Builder()
            .url(target(upstream, relayHost))
            .apply {
                if (upstream.probeWithBody) get() else head()
                if (relayHost == null) header(ImageRelayInterceptor.BYPASS_HEADER, "1")
            }
            .build()
        val startedAt = runtime.now()
        val result = runCatching {
            client.newCall(request).execute().use { response ->
                val bytes = response.body?.bytes()?.size?.toLong() ?: 0L
                ImageProbeResult(
                    ok = true,
                    elapsedMs = runtime.now() - startedAt,
                    statusCode = response.code,
                    error = null,
                    atMillis = startedAt,
                    bytes = bytes,
                )
            }
        }.getOrElse { error ->
            ImageProbeResult(
                ok = false,
                elapsedMs = runtime.now() - startedAt,
                statusCode = null,
                error = describe(error),
                atMillis = startedAt,
            )
        }
        // 探测本身就是一次测速：拿到的读数直接进线路状态（够快会自动切回直连）
        if (result.ok && result.bytes > 0) {
            controller.recordSpeed(upstream, relay = relayHost != null, result.bytes, result.elapsedMs)
        }
        return result
    }

    private fun target(upstream: ImageUpstream, relayHost: String?) = upstream.probeUrl.toHttpUrl().let { url ->
        if (relayHost == null) {
            url
        } else {
            url.newBuilder()
                .host(relayHost)
                .encodedPath(upstream.relayPath(url.encodedPath))
                .build()
        }
    }

    private fun describe(error: Throwable): String = error.describeChain(levels = 2, maxChars = MAX_FAILURE_CHARS)

    private companion object {
        const val MAX_FAILURE_CHARS = 80
    }
}
