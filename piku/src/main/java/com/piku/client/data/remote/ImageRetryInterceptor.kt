package com.piku.client.data.remote

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import java.io.InterruptedIOException
import java.net.ProtocolException
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
                // 异常出在 Coil 侧，OkHttp 拦截器链看不到 —— 所以换边必须挂在这里：
                // 直连失败切中继，中继断流切直连，切完这次重试就走新线路
                if (switchRouteOnTransferFailure(upstream, cause, routeController)) {
                    val to = if (upstream != null && routeController?.useRelay(upstream) == true) "中继" else "直连"
                    diagnostics.warn(
                        "image route ${upstream?.host} -> $to｜取图传输失败：" +
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

/**
 * 传输类失败（超时 / 连接重置 / 握手 / body 截断）才算线路问题；HTTP 状态码与取消都不算。
 * okhttp 对"body 传到一半断了"抛的是 [ProtocolException]（unexpected end of stream），
 * 大图恰好死成这样，不能漏。
 */
internal fun isTransferFailure(cause: Throwable?): Boolean =
    generateSequence(cause) { it.cause }.any {
        it is InterruptedIOException || it is SocketTimeoutException ||
            it is SocketException || it is SSLException || it is ProtocolException
    }

/**
 * 取图传输失败时把这条上游换边（AUTO 模式下才生效）：直连失败切中继，中继失败切直连。
 * @return 是否真的换了边：非传输类失败、手动模式、被冷却拦下都返回 false
 */
internal fun switchRouteOnTransferFailure(
    upstream: ImageUpstream?,
    cause: Throwable?,
    controller: ImageRouteController?,
): Boolean {
    if (upstream == null || controller == null) return false
    if (!isTransferFailure(cause)) return false
    val wasRelay = controller.useRelay(upstream)
    if (wasRelay) controller.markRelayFailed(upstream) else controller.markDirectFailed(upstream)
    return controller.useRelay(upstream) != wasRelay
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
