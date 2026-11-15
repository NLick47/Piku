package com.piku.client.core.network.http
import com.piku.client.core.network.diag.NetworkDiagnostics
import com.piku.client.core.network.dns.DoHDns

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * 打上这个 tag 的请求，**超时类** I/O 重试由外层负责（图片线路切换 + Coil 重试）。
 *
 * 超时重试要再吃满一个连接超时（一次直连最坏 10s），会把调用方留给线路切换和
 * 原生通道的预算吃光；而「连接被拒」这类快速失败的重试几乎不花时间，仍然值得保留。
 */
object OuterLayerOwnsRetry

class RetryInterceptor internal constructor(
    private val dns: DoHDns,
    private val runtime: NetworkRuntime = NetworkRuntime(),
    private val policy: RetryPolicy = RetryPolicy(),
    private val diagnostics: NetworkDiagnostics = NetworkDiagnostics(),
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val retryableMethod = request.method == "GET"
        var attempts = RetryPolicy.Attempts()
        while (true) {
            if (chain.call().isCanceled()) throw IOException("Canceled")
            try {
                val response = chain.proceed(request)
                val decision = policy.httpFailure(response.code, retryableMethod, attempts)
                if (decision !is RetryPolicy.Decision.Retry) return response
                attempts = decision.attempts
                response.close()
                diagnostics.warn(
                    "retry http=${response.code} path=${request.url.encodedPath} attempt=${attempts.http}",
                )
                runtime.sleeper(decision.delayMs)
            } catch (e: IOException) {
                if (chain.call().isCanceled()) throw e
                if (request.tag(OuterLayerOwnsRetry::class.java) != null && isSlowFailure(e)) throw e
                val decision = policy.ioFailure(e, retryableMethod, attempts)
                if (decision !is RetryPolicy.Decision.Retry) {
                    diagnostics.warn(
                        "give up ${request.method} path=${request.url.encodedPath} " +
                            "${e.javaClass.simpleName}: ${e.message}",
                    )
                    throw e
                }
                attempts = decision.attempts
                if (decision.replaceAddress) dns.forceReResolve(request.url.host)
                diagnostics.warn(
                    "retry ${request.method} path=${request.url.encodedPath} " +
                        "${e.javaClass.simpleName}: ${e.message}",
                )
                runtime.sleeper(decision.delayMs)
            }
        }
    }

    /** 超时类失败：重试要再吃满一个连接超时，对有时间预算的调用方不划算 */
    private fun isSlowFailure(e: IOException): Boolean =
        e is SocketTimeoutException || e is InterruptedIOException
}
