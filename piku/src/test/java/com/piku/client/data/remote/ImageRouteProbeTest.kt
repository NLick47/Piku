package com.piku.client.data.remote

import com.piku.client.data.local.InMemorySharedPreferences
import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.ImageRouteMode
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class ImageRouteProbeTest {

    private var now = 1_000_000L
    private val runtime = NetworkRuntime(now = { now }, sleeper = {})
    private val prefs = InMemorySharedPreferences()
    private val settings = SettingsRepository(InMemorySharedPreferences())
    private val controller = ImageRouteController(settings, prefs, runtime)

    private val calls = mutableListOf<Request>()

    private fun probe(outcome: () -> Response) = ImageRouteProbe(
        client = object : Call.Factory {
            override fun newCall(request: Request): Call {
                calls += request
                return FakeCall(request, executeResult = outcome)
            }
        },
        controller = controller,
    )

    private fun response(code: Int): Response =
        okResponse(Request.Builder().url(ImageRelayInterceptor.PROBE_URL).build(), code)

    @Test
    fun anyHttpResponseMeansDirectWorks() {
        probe { response(200) }.run()

        assertFalse(controller.useRelay)
    }

    @Test
    fun errorResponseStillMeansDirectWorks() {
        // 只要拿得到响应就说明链路通：否则资源被删/改名时会把 AUTO 用户全推去中转
        probe { response(404) }.run()

        assertFalse(controller.useRelay)
    }

    @Test
    fun unreachableDirectSwitchesToRelay() {
        probe { throw SocketTimeoutException("timeout") }.run()

        assertTrue(controller.useRelay)
    }

    @Test
    fun probeIsSkippedInManualModes() {
        settings.setImageRouteMode(ImageRouteMode.DIRECT)

        probe { response(200) }.run()

        assertEquals(emptyList<Request>(), calls)
    }

    @Test
    fun probeCanTargetARelayHostWithoutTheBypassHeader() {
        probe { response(200) }.probe("pic-relay.cyou")

        val request = calls.single()
        assertEquals("pic-relay.cyou", request.url.host)
        assertEquals("HEAD", request.method)
        assertEquals(null, request.header(ImageRelayInterceptor.BYPASS_HEADER))
    }

    @Test
    fun failedProbeCarriesTheReason() {
        val result = probe { throw SocketTimeoutException("connect timed out") }.probe(host = null)

        assertEquals(false, result.ok)
        assertEquals("SocketTimeoutException: connect timed out", result.error)
    }

    @Test
    fun probeUsesHeadWithBypassHeader() {
        probe { response(200) }.run()

        val request = calls.single()
        assertEquals("HEAD", request.method)
        assertEquals(ImageRelayInterceptor.PROBE_URL, request.url.toString())
        assertNotNull(request.header(ImageRelayInterceptor.BYPASS_HEADER))
    }
}
