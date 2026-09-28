package com.piku.client.data.remote

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class ImageRelayInterceptor(
    private val controller: ImageRouteController,
    private val diagnostics: NetworkDiagnostics = NetworkDiagnostics(),
) : Interceptor {

    // 中转健康记忆挂在 controller 上：诊断报告要能直接读到它，不必从 OkHttpClient 里掏拦截器
    private val relayHealth = controller.relayHealth

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != CDN_HOST) return chain.proceed(request)

        if (request.header(BYPASS_HEADER) != null) {
            return chain.proceed(request.newBuilder().removeHeader(BYPASS_HEADER).build())
        }

        if (controller.useRelay) {
            tryRelays(chain, request)?.let { return it }
            controller.markRelayFailed()
            return chain.proceed(request)
        }
        return try {
            chain.proceed(request)
        } catch (e: IOException) {
            controller.markDirectFailed()
            tryRelays(chain, request) ?: throw e
        }
    }

    private fun tryRelays(chain: Interceptor.Chain, request: Request): Response? {
        for (host in relayHealth.candidates()) {
            val resp = try {
                chain.proceed(rewrite(request, host))
            } catch (_: IOException) {
                // 整体已超时/取消（如 callTimeout）不算中继故障：不记失败，
                // 也不在被取消的调用上继续试下一条
                if (chain.call().isCanceled()) return null
                relayHealth.onFailure(host)
                diagnostics.warn("relay failed host=$host")
                continue
            }
            if (resp.isSuccessful) {
                relayHealth.onSuccess(host)
                return resp
            }
            // 非 2xx（404/502）只换这张图的下一个，不记在这条线路上
            resp.close()
        }
        return null
    }

    private fun rewrite(request: Request, host: String): Request =
        request.newBuilder()
            .url(request.url.newBuilder().host(host).build())
            .build()

    companion object {
        const val CDN_HOST = "cdn.poipiku.com"
        const val BYPASS_HEADER = "X-Piku-Direct"
        const val PROBE_URL = "https://cdn.poipiku.com/assets/img/poipiku_icon_512x512_2.png"

        val RELAY_HOSTS = listOf(
            "pic-relay.cyou",
            "piku-img.pages.dev",
        )
    }
}
