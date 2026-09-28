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
)

class ImageRouteProbe(
    private val client: Call.Factory,
    private val controller: ImageRouteController,
    private val runtime: NetworkRuntime = NetworkRuntime(),
) {

    /** 启动时的自动探测：手动选了直连/中转的用户跳过，省一次请求 */
    fun run() {
        if (!controller.shouldRunProbe()) return
        controller.applyProbe(probe(host = null))
    }

    /** 探测直连（[host] 为 null）或某条中转线路 */
    fun probe(host: String?): ImageProbeResult {
        val probeUrl = ImageRelayInterceptor.PROBE_URL.toHttpUrl()
        val request = Request.Builder()
            .url(if (host == null) probeUrl else probeUrl.newBuilder().host(host).build())
            .head()
            // 直连探测要绕过中转改写，否则测的就不是直连
            .apply { if (host == null) header(ImageRelayInterceptor.BYPASS_HEADER, "1") }
            .build()
        val startedAt = runtime.now()
        return runCatching {
            client.newCall(request).execute().use { response ->
                ImageProbeResult(
                    ok = true,
                    elapsedMs = runtime.now() - startedAt,
                    statusCode = response.code,
                    error = null,
                    atMillis = startedAt,
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
    }

    private fun describe(error: Throwable): String = error.describeChain(levels = 2, maxChars = MAX_FAILURE_CHARS)

    private companion object {
        const val MAX_FAILURE_CHARS = 80
    }
}
