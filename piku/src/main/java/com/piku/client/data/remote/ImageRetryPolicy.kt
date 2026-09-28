package com.piku.client.data.remote

import coil3.network.HttpException
import java.io.IOException
import java.util.concurrent.ThreadLocalRandom

internal class ImageRetryPolicy(
    private val maxRetries: Int = MAX_RETRIES,
    /** 第 n 次重试前的等待，注入以便断言精确序列 */
    private val backoffMs: (Int) -> Long = { attempt -> backoff(attempt) },
) {

    /** 值得再取一次时返回等待毫秒；null 表示就此放弃 */
    fun nextRetryMs(cause: Throwable?, retries: Int): Long? = when {
        retries >= maxRetries -> null
        !isTransportFailure(cause) -> null
        else -> backoffMs(retries + 1)
    }

    /** 传输失败：异常链里有 IOException，且不是取消、也不是 HTTP 状态错误 */
    fun isTransportFailure(cause: Throwable?): Boolean {
        val chain = generateSequence(cause) { it.cause }.toList()
        if (chain.any { it is HttpException || it.isCancellation() }) return false
        return chain.any { it is IOException }
    }

    private companion object {
        /** 重试 3 次：加上首次请求，一张图最多取 4 次 */
        const val MAX_RETRIES = 3
        val BACKOFF_MS = longArrayOf(500L, 1_500L, 3_000L)
        const val JITTER_MS = 120L

        /** 退避逐次拉长，加抖动避免整屏图片同时重来 */
        fun backoff(attempt: Int): Long =
            BACKOFF_MS[(attempt - 1).coerceAtMost(BACKOFF_MS.lastIndex)] +
                ThreadLocalRandom.current().nextLong(0L, JITTER_MS)
    }
}
