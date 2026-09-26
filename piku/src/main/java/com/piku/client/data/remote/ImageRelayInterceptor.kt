package com.piku.client.data.remote

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class ImageRelayInterceptor(
    private val controller: ImageRouteController,
    private val relayHosts: List<String> = RELAY_HOSTS,
) : Interceptor {

    // 中继健康记忆：连不上的冷却期内跳过，可用的一次性提到最前，避免每张图都先踩死路
    private val cooldownUntil = ConcurrentHashMap<String, Long>()
    private val order = AtomicReference(relayHosts)

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
        val now = System.currentTimeMillis()
        for (host in order.get()) {
            if (now < (cooldownUntil[host] ?: 0L)) continue
            val resp = try {
                chain.proceed(rewrite(request, host))
            } catch (_: IOException) {
                // 整体已超时/取消（如 callTimeout）不算中继故障，否则会把本来健康的中继也冷却掉、
                // 且被取消的后续尝试会连锁把每个中继都记一遍。
                if (chain.call().isCanceled()) return null
                cooldownUntil[host] = System.currentTimeMillis() + RELAY_COOLDOWN_MS
                continue
            }
            if (resp.isSuccessful) {
                cooldownUntil.remove(host)
                order.updateAndGet { cur -> listOf(host) + cur.filter { it != host } }
                return resp
            }
            // 非 2xx（404/502）只换这张图的下一个，不冷却整个中继
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

        private const val RELAY_COOLDOWN_MS = 30_000L
    }
}
