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
    private val runtime = NetworkRuntime(now = { now }, monotonicNow = { now }, sleeper = {})
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
        okResponse(Request.Builder().url(ImageUpstream.POIPIKU.probeUrl).build(), code)

    @Test
    fun anyHttpResponseMeansDirectWorks() {
        probe { response(200) }.run()

        assertFalse(controller.useRelay(ImageUpstream.POIPIKU))
    }

    @Test
    fun errorResponseStillMeansDirectWorks() {
        // 只要拿得到响应就说明链路通：否则资源被删/改名时会把 AUTO 用户全推去中转
        probe { response(404) }.run()

        assertFalse(controller.useRelay(ImageUpstream.POIPIKU))
    }

    @Test
    fun unreachableDirectSwitchesToRelay() {
        probe { throw SocketTimeoutException("timeout") }.run()

        assertTrue(controller.useRelay(ImageUpstream.POIPIKU))
    }

    @Test
    fun probeIsSkippedInManualModes() {
        settings.setImageRouteMode(ImageRouteMode.DIRECT)

        probe { response(200) }.run()

        assertEquals(emptyList<Request>(), calls)
    }

    @Test
    fun probeCanTargetARelayHostWithoutTheBypassHeader() {
        probe { response(200) }.probe(ImageUpstream.POIPIKU, relayHost = "pic-relay.cyou")

        val request = calls.single()
        assertEquals("pic-relay.cyou", request.url.host)
        assertEquals("HEAD", request.method)
        assertEquals(null, request.header(ImageRelayInterceptor.BYPASS_HEADER))
    }

    @Test
    fun failedProbeCarriesTheReason() {
        val result = probe { throw SocketTimeoutException("connect timed out") }.probe(ImageUpstream.POIPIKU, relayHost = null)

        assertEquals(false, result.ok)
        assertEquals("SocketTimeoutException: connect timed out", result.error)
    }

    @Test
    fun missingProbeImageDoesNotMoveThePixivRoute() {
        // 大图探测返回 404：证明不了"传得完"，就不能拿它改线路（否则探测图被删会误判）
        val result = probe { response(404) }.probe(ImageUpstream.PIXIV, relayHost = null)

        assertEquals(null, ImageRouteProbe(
            client = object : Call.Factory {
                override fun newCall(request: Request) = FakeCall(request)
            },
            controller = controller,
        ).verdict(ImageUpstream.PIXIV, result))
    }

    @Test
    fun smallProbeResponseMeansDirectWorksEvenIfTheAssetIsGone() {
        val result = probe { response(404) }.probe(ImageUpstream.POIPIKU, relayHost = null)

        assertEquals(true, ImageRouteProbe(
            client = object : Call.Factory {
                override fun newCall(request: Request) = FakeCall(request)
            },
            controller = controller,
        ).verdict(ImageUpstream.POIPIKU, result))
    }

    @Test
    fun pixivProbeSuccessMeansDirectWorks() {
        val result = probe { response(200) }.probe(ImageUpstream.PIXIV, relayHost = null)

        assertEquals(true, ImageRouteProbe(
            client = object : Call.Factory {
                override fun newCall(request: Request) = FakeCall(request)
            },
            controller = controller,
        ).verdict(ImageUpstream.PIXIV, result))
    }

    @Test
    fun probeUsesHeadWithBypassHeader() {
        probe { response(200) }.run()

        val request = calls.single()
        assertEquals("HEAD", request.method)
        assertEquals(ImageUpstream.POIPIKU.probeUrl, request.url.toString())
        assertNotNull(request.header(ImageRelayInterceptor.BYPASS_HEADER))
    }

    @Test
    fun pixivProbeReadsTheBodyBecauseOnlyBigImagesTellTheTruth() {
        probe { response(200) }.probe(ImageUpstream.PIXIV, relayHost = null)

        val request = calls.single()
        // 只看响应头的话，直连"连得上但传不完"也会被判成可用
        assertEquals("GET", request.method)
        assertEquals(ImageUpstream.PIXIV.host, request.url.host)
        assertTrue(request.url.encodedPath.endsWith("_master1200.jpg"))
    }

    @Test
    fun pixivRelayProbeGoesThroughThePximgPrefix() {
        probe { response(200) }.probe(ImageUpstream.PIXIV, relayHost = "piku-img.pages.dev")

        assertEquals(
            "/pximg${ImageUpstream.PIXIV.probePath}",
            calls.single().url.encodedPath,
        )
    }

    @Test
    fun pixivVerdictOnlyTouchesThePixivRoute() {
        // 诊断页拿到结论后落状态（和 NetworkDiagnosis 里一样）
        val result = probe { throw SocketTimeoutException("timeout") }.probe(ImageUpstream.PIXIV, relayHost = null)
        controller.applyProbe(ImageUpstream.PIXIV, result.ok)

        assertTrue(controller.useRelay(ImageUpstream.PIXIV))
        assertFalse("poipiku 那条不该被 pixiv 的探测结果带偏", controller.useRelay(ImageUpstream.POIPIKU))
    }

    @Test
    fun startupProbeStaysOnPoipikuAndKeepsPixivDirect() {
        probe { response(200) }.run()

        val request = calls.single()
        assertEquals(ImageUpstream.POIPIKU.host, request.url.host)
        assertFalse(controller.useRelay(ImageUpstream.PIXIV))
    }
}
