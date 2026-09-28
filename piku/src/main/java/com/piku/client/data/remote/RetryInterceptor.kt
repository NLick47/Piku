package com.piku.client.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

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
}
