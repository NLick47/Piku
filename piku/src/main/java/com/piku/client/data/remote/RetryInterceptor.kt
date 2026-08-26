package com.piku.client.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ThreadLocalRandom

/**
 * 双通道重试：
 * - IOException（连通性问题）：重试前淘汰当前赢家（forceReResolve），
 *   下一次解析从保留的解析缓存 + 黑名单过滤中换 IP，不重复查询 DNS；
 *   退避重试；读超时是重量级失败，最多只重试一次。
 * - HTTP 429/5xx（服务端正常应答）：仅退避重试，不换 IP；耗尽后抛回
 *   最后一次 Response，保持错误语义无损。
 */
class RetryInterceptor(
    private val dns: DoHDns,
    private val maxAttempts: Int = 3,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)

        var ioAttempts = 0
        var timeoutAttempts = 0
        var httpAttempts = 0
        while (true) {
            if (chain.call().isCanceled()) throw IOException("Canceled")
            try {
                val response = chain.proceed(request)
                if (!shouldRetryHttp(response.code)) return response
                httpAttempts++
                if (httpAttempts >= maxAttempts) return response
                response.close()
                sleep(backoffMillis(httpAttempts, http = true))
            } catch (e: IOException) {
                if (chain.call().isCanceled()) throw e
                val timedOut = e is SocketTimeoutException
                val used = if (timedOut) ++timeoutAttempts else ++ioAttempts
                val limit = if (timedOut) minOf(maxAttempts, 2) else maxAttempts
                if (used >= limit) throw e
                dns.forceReResolve(request.url.host)
                sleep(backoffMillis(used, http = false))
            }
        }
    }

    private fun shouldRetryHttp(code: Int): Boolean =
        code == 429 || code in 500..599

    private fun backoffMillis(attempt: Int, http: Boolean): Long {
        val base = if (http && attempt == 1) 1_000L else 300L
        val exp = base shl (attempt - 1).coerceAtMost(3)
        return exp + ThreadLocalRandom.current().nextLong(0, 100)
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
