package com.piku.client.data.remote

import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

class ImageNetworkInterceptorTest {

    private var now = 1_000L
    private val runtime = NetworkRuntime(now = { now }, sleeper = {})
    private val diagnostics = ImageDiagnostics(runtime)
    private val interceptor = ImageNetworkInterceptor(diagnostics, runtime)

    private val cdnRequest = Request.Builder()
        .url("https://${ImageRelayInterceptor.CDN_HOST}/img/1_360.jpg")
        .build()
    private val relayRequest = Request.Builder()
        .url("https://pic-relay.cyou/img/1_360.jpg")
        .build()

    private fun chain(request: Request, results: List<Any>, canceled: Boolean = false) =
        FakeChain(request, FakeCall(request, canceled), results)

    @Test
    fun handshakeFailureIsReportedAsTheSniStyleSignature() {
        val chain = chain(cdnRequest, listOf(SSLHandshakeException("Connection reset")))

        assertThrows(SSLHandshakeException::class.java) { interceptor.intercept(chain) }

        val line = diagnostics.summary().last()
        assertTrue(line.contains("握手/连接被重置（常见于 SNI 阻断）"))
        assertTrue(line.contains("直连 ${ImageRelayInterceptor.CDN_HOST}"))
    }

    @Test
    fun connectFailureIsReportedAsUnreachable() {
        val chain = chain(cdnRequest, listOf(ConnectException("Connection refused")))

        assertThrows(ConnectException::class.java) { interceptor.intercept(chain) }

        assertTrue(diagnostics.summary().any { it.contains("连接失败（IP 不通或被拦）  1") })
    }

    @Test
    fun timeoutIsReportedAsTimeout() {
        val chain = chain(cdnRequest, listOf(SocketTimeoutException("connect timed out")))

        assertThrows(SocketTimeoutException::class.java) { interceptor.intercept(chain) }

        assertTrue(diagnostics.summary().any { it.contains("超时（含整体超时）  1") })
    }

    @Test
    fun httpErrorKeepsTheStatusCode() {
        val chain = chain(cdnRequest, listOf(okResponse(cdnRequest, 404)))

        interceptor.intercept(chain)

        assertTrue(diagnostics.summary().any { it.contains("HTTP 错误  1") })
        assertTrue(diagnostics.summary().last().contains("HTTP 404"))
    }

    @Test
    fun canceledRequestIsNotAFailure() {
        val chain = chain(cdnRequest, listOf(IOException("canceled")), canceled = true)

        assertThrows(IOException::class.java) { interceptor.intercept(chain) }

        assertTrue(diagnostics.summary().first().contains("取消 1"))
        assertFalse(diagnostics.summary().any { it.contains("最近失败") })
    }

    @Test
    fun relayedRequestIsAttributedToTheRelayHost() {
        val chain = chain(relayRequest, listOf(okResponse(relayRequest)))

        interceptor.intercept(chain)

        assertTrue(diagnostics.summary().any { it.contains("线路：直连 0  中转 1（pic-relay.cyou 1）") })
    }
}
