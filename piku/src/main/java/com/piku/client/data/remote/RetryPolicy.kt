package com.piku.client.data.remote

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ThreadLocalRandom
import javax.net.ssl.SSLHandshakeException

internal class RetryPolicy(
    private val maxAttempts: Int = MAX_ATTEMPTS,
    /** 退避抖动，注入以便断言精确的退避序列 */
    private val jitterMs: () -> Long = { ThreadLocalRandom.current().nextLong(0, 100) },
) {

    /** 各通道已用的尝试次数：读超时与普通 IO、HTTP 状态各有独立额度 */
    data class Attempts(val io: Int = 0, val timeout: Int = 0, val http: Int = 0)

    sealed interface Decision {

        /** 再试一次：等待 [delayMs]，[replaceAddress] 表示该换一个 IP 了 */
        data class Retry(
            val attempts: Attempts,
            val delayMs: Long,
            val replaceAddress: Boolean,
        ) : Decision

        /** 不再重试：IOException 直接抛回，HTTP 响应原样返回 */
        data object GiveUp : Decision
    }

    fun ioFailure(error: IOException, retryableMethod: Boolean, attempts: Attempts): Decision {
        // 非 GET 只在"确定没送达服务端"的失败下重试：读超时与流中断可能已经送达，
        // 重试会有重复副作用（重复点赞、重复建作品）
        if (!retryableMethod && !failedBeforeSend(error)) return Decision.GiveUp
        val timedOut = error is SocketTimeoutException
        val next = if (timedOut) attempts.copy(timeout = attempts.timeout + 1) else attempts.copy(io = attempts.io + 1)
        val used = if (timedOut) next.timeout else next.io
        // 读超时是重量级失败，最多重试一次
        val limit = if (timedOut) minOf(maxAttempts, MAX_TIMEOUT_ATTEMPTS) else maxAttempts
        if (used >= limit) return Decision.GiveUp
        return Decision.Retry(next, backoffMillis(used, http = false), replaceAddress = true)
    }

    fun httpFailure(code: Int, retryableMethod: Boolean, attempts: Attempts): Decision {
        // HTTP 状态重试只对 GET：POST 收到 5xx 时服务端可能已经处理过这次请求
        if (!retryableMethod || !shouldRetryHttp(code)) return Decision.GiveUp
        val next = attempts.copy(http = attempts.http + 1)
        if (next.http >= maxAttempts) return Decision.GiveUp
        return Decision.Retry(next, backoffMillis(next.http, http = true), replaceAddress = false)
    }

    /**
     * 该失败是否确定发生在请求送达服务端之前。
     *
     * - [UnknownHostException] / [ConnectException]：连接根本没建立起来；
     * - [SSLHandshakeException]：握手在 HTTP 报文之前完成，失败即请求从未送达。
     *
     * 特意不含 NoRouteToHostException：同一个 errno 也可能在**写入途中**抛出，
     * 那样重试图片上传就有重复建作品的风险。也特意取 [SSLHandshakeException] 具体类而不是
     * [javax.net.ssl.SSLException]：后者同样会在读取响应时抛出，那时请求早已送达。
     */
    fun failedBeforeSend(error: IOException): Boolean = when (error) {
        is UnknownHostException, is ConnectException, is SSLHandshakeException -> true
        else -> false
    }

    private fun shouldRetryHttp(code: Int): Boolean = code == 429 || code in 500..599

    private fun backoffMillis(attempt: Int, http: Boolean): Long {
        val base = if (http && attempt == 1) HTTP_FIRST_BACKOFF_MS else IO_BACKOFF_MS
        val capped = base shl (attempt - 1).coerceAtMost(MAX_SHIFT)
        return capped + jitterMs()
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val MAX_TIMEOUT_ATTEMPTS = 2
        const val IO_BACKOFF_MS = 300L
        const val HTTP_FIRST_BACKOFF_MS = 1_000L
        const val MAX_SHIFT = 3
    }
}
