package com.piku.client.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

class ImageNetworkInterceptor(
    private val diagnostics: ImageDiagnostics,
    private val runtime: NetworkRuntime = NetworkRuntime(),
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startedAt = runtime.now()
        return try {
            val response = chain.proceed(request)
            diagnostics.recordAttempt(
                host = request.url.host,
                path = request.url.encodedPath,
                outcome = if (response.isSuccessful) ImageDiagnostics.Outcome.OK else ImageDiagnostics.Outcome.HTTP_ERROR,
                detail = "HTTP ${response.code}",
                elapsedMs = runtime.now() - startedAt,
                relay = request.url.host != ImageRelayInterceptor.CDN_HOST,
            )
            response
        } catch (e: IOException) {
            // 整体超时或页面滑走都会取消在途请求：那不是线路故障
            val cancelled = chain.call().isCanceled()
            diagnostics.recordAttempt(
                host = request.url.host,
                path = request.url.encodedPath,
                outcome = if (cancelled) ImageDiagnostics.Outcome.CANCELLED else classify(e),
                detail = e.describeChain(levels = 2, maxChars = 90),
                elapsedMs = runtime.now() - startedAt,
                relay = request.url.host != ImageRelayInterceptor.CDN_HOST,
            )
            throw e
        }
    }

    private fun classify(e: IOException): ImageDiagnostics.Outcome = when (e) {
        // 握手没过去：证书不匹配、或中间设备在 ClientHello 后重置（SNI 阻断的典型样子）
        is SSLException -> ImageDiagnostics.Outcome.HANDSHAKE_FAILED
        // 连接没建起来：IP 不通、端口被拦
        is ConnectException -> ImageDiagnostics.Outcome.CONNECT_FAILED
        is SocketTimeoutException -> ImageDiagnostics.Outcome.TIMEOUT
        is SocketException ->
            if (e.message?.contains("reset", ignoreCase = true) == true) {
                ImageDiagnostics.Outcome.HANDSHAKE_FAILED
            } else {
                ImageDiagnostics.Outcome.CONNECT_FAILED
            }

        else -> ImageDiagnostics.Outcome.TRANSFER_FAILED
    }
}
