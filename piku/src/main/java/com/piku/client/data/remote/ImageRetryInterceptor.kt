package com.piku.client.data.remote

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ImageRetryInterceptor internal constructor(
    private val runtime: NetworkRuntime = NetworkRuntime(),
    private val policy: ImageRetryPolicy = ImageRetryPolicy(),
    private val diagnostics: NetworkDiagnostics = NetworkDiagnostics(),
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        // 只有网络取图才重试：本地文件读失败，再取几次也是同一个结果
        val url = (chain.request.data as? String)?.toHttpUrlOrNull() ?: return chain.proceed()
        val path = "${url.host}${url.encodedPath}"
        return retryFetch(
            policy = policy,
            fetch = { chain.proceed() },
            failureOf = { (it as? ErrorResult)?.throwable },
            onRetry = { cause, attempt, delayMs ->
                diagnostics.warn(
                    "image retry attempt=$attempt path=$path wait=${delayMs}ms " +
                        cause?.describeChain(levels = 1, maxChars = 80),
                )
            },
            sleep = runtime.sleeper,
        )
    }
}

/**
 * 失败就再取一次的循环：结果与失败原因分别由 [fetch]、[failureOf] 给出。
 *
 * 不碰 Coil 类型，JVM 单测可以直接驱动这个循环，断言重试次数与放弃时机。
 */
internal suspend fun <T> retryFetch(
    policy: ImageRetryPolicy,
    fetch: suspend () -> T,
    failureOf: (T) -> Throwable?,
    onRetry: (Throwable?, Int, Long) -> Unit = { _, _, _ -> },
    sleep: (Long) -> Unit = {},
): T {
    var retries = 0
    while (true) {
        val result = fetch()
        val failure = failureOf(result)
        val delayMs = policy.nextRetryMs(failure, retries) ?: return result
        retries++
        onRetry(failure, retries, delayMs)
        sleep(delayMs)
    }
}
