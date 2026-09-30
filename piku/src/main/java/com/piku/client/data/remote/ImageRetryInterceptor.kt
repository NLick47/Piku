package com.piku.client.data.remote

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ImageRetryInterceptor internal constructor(
    private val runtime: NetworkRuntime = NetworkRuntime(),
    private val policy: ImageRetryPolicy = ImageRetryPolicy(),
    private val diagnostics: NetworkDiagnostics = NetworkDiagnostics(),
    private val routeController: ImageRouteController? = null,
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        // 只有网络取图才重试：本地文件读失败，再取几次也是同一个结果
        val url = (chain.request.data as? String)?.toHttpUrlOrNull() ?: return chain.proceed()
        val path = "${url.host}${url.encodedPath}"
        val upstream = ImageUpstream.of(url.host)
        return retryFetch(
            policy = policy,
            fetch = { chain.proceed() },
            failureOf = { (it as? ErrorResult)?.throwable },
            onRetry = { cause, attempt, delayMs ->
                // **大图直连失败发生在"读 body"那一刻**（callTimeout 掐的就是这一段），
                // 异常出在 Coil 侧，OkHttp 拦截器链看不到 —— 所以"直连失败就切中继"
                // 只能挂在这里：切完这次重试就会走中继（OkHttp 那层按状态改写）
                if (switchRouteOnTransferFailure(upstream, cause, routeController)) {
                    diagnostics.warn(
                        "image route ${upstream?.host} -> 中继｜直连取图失败：" +
                            cause?.describeChain(levels = 1, maxChars = 60),
                    )
                }
                diagnostics.warn(
                    "image retry attempt=$attempt path=$path wait=${delayMs}ms " +
                        cause?.describeChain(levels = 1, maxChars = 80),
                )
            },
            sleep = runtime.sleeper,
        )
    }
}

/** 传输类失败（超时 / 连接重置 / 握手）才算线路问题；HTTP 状态码与取消都不算 */
internal fun isTransferFailure(cause: Throwable?): Boolean =
    generateSequence(cause) { it.cause }.any {
        it is InterruptedIOException || it is SocketTimeoutException ||
            it is SocketException || it is SSLException
    }

/**
 * 直连取图失败时把这条上游切到中继（AUTO 模式下才生效）。
 * @return 是否真的切了：已经在中继上、或不是传输类失败，都返回 false
 */
internal fun switchRouteOnTransferFailure(
    upstream: ImageUpstream?,
    cause: Throwable?,
    controller: ImageRouteController?,
): Boolean {
    if (upstream == null || controller == null) return false
    if (controller.useRelay(upstream)) return false
    if (!isTransferFailure(cause)) return false
    controller.markDirectFailed(upstream)
    return controller.useRelay(upstream)
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
