package com.piku.client.data.remote

import com.piku.client.data.local.InMemorySharedPreferences
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketException
import javax.net.ssl.SSLHandshakeException

class DoHEventListenerTest {

    private val prefs = InMemorySharedPreferences()
    private val dns = DoHDns(prefs)
    private val diagnostics = NetworkDiagnostics(NetworkRuntime(now = { 0L }, sleeper = {}))
    private val listener = DoHEventListener(dns, diagnostics)

    private val ipA = InetAddress.getByName("1.1.1.1")
    private val request = Request.Builder().url("https://poipiku.com/").build()
    private val socketAddress = InetSocketAddress(ipA, 443)

    private fun persisted(): String? = prefs.getString("trusted_dns_ip_poipiku.com", null)

    private fun connectFailed(canceled: Boolean, error: IOException) {
        listener.connectFailed(
            FakeCall(request, canceled = canceled),
            socketAddress,
            Proxy.NO_PROXY,
            null,
            error,
        )
    }

    @Test
    fun canceledConnectFailureIsNotReported() {
        dns.reportSuccess(HOST, ipA)

        // 用户离开页面、图片总超时都会取消在途连接：那不是地址的错
        connectFailed(canceled = true, error = SSLHandshakeException("handshake aborted"))

        assertEquals(true, persisted()?.startsWith("1.1.1.1|"))
        assertTrue("取消不该写进诊断", diagnostics.snapshot().isEmpty())
    }

    @Test
    fun handshakeFailureDropsTheTrustedAddress() {
        dns.reportSuccess(HOST, ipA)

        connectFailed(canceled = false, error = SSLHandshakeException("bad certificate"))

        assertFalse(persisted()?.contains("1.1.1.1") ?: false)
        assertTrue(diagnostics.snapshot().single().message.contains("connect failed host=poipiku.com"))
    }

    @Test
    fun plainConnectFailureKeepsTheTrustedAddress() {
        dns.reportSuccess(HOST, ipA)

        connectFailed(canceled = false, error = SocketException("connection reset"))

        // 连接类失败只是缓刑，不该动持久化信任列表
        assertTrue(persisted()?.startsWith("1.1.1.1|") ?: false)
        assertEquals(1, diagnostics.snapshot().size)
    }

    @Test
    fun canceledResponseFailureIsNotRecorded() {
        listener.responseFailed(FakeCall(request, canceled = true), IOException("closed"))

        assertTrue(diagnostics.snapshot().isEmpty())
    }

    @Test
    fun responseFailureIsRecordedWhenTheCallIsAlive() {
        listener.responseFailed(FakeCall(request, canceled = false), IOException("closed"))

        assertTrue(diagnostics.snapshot().single().message.contains("response failed host=poipiku.com"))
    }

    private companion object {
        const val HOST = "poipiku.com"
    }
}
