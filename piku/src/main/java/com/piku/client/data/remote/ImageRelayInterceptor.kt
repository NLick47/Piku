package com.piku.client.data.remote

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class ImageRelayInterceptor(
    private val controller: ImageRouteController,
    private val relayHosts: List<String> = RELAY_HOSTS,
) : Interceptor {

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
        for (host in relayHosts) {
            try {
                val resp = chain.proceed(rewrite(request, host))
                // 只有 2xx 才算中转成功：404/502 这类 HTTP 响应不能直接当作可用，
                // 否则会漏掉"试下一个中转"和"回退直连"，导致图裂且不自愈。
                if (resp.isSuccessful) return resp
                resp.close()
            } catch (_: IOException) {
                // 换下一个中转地址
            }
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
