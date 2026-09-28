package com.piku.client.data.remote

import com.piku.client.data.local.InMemorySharedPreferences
import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.ImageRouteMode
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class ImageRelayInterceptorTest {

    private var now = 0L
    private val runtime = NetworkRuntime(now = { now }, sleeper = {})
    private val prefs = InMemorySharedPreferences()
    private val settings = SettingsRepository(InMemorySharedPreferences())
    private val controller = ImageRouteController(settings, prefs, runtime)

    private val cdnRequest = Request.Builder()
        .url("https://${ImageRelayInterceptor.CDN_HOST}/assets/img/a.png")
        .build()

    private fun interceptor(hosts: List<String> = ImageRelayInterceptor.RELAY_HOSTS) =
        ImageRelayInterceptor(ImageRouteController(settings, prefs, runtime, hosts))

    private fun chain(
        request: Request = cdnRequest,
        results: List<Any>,
        canceled: Boolean = false,
    ) = FakeChain(request, FakeCall(request, canceled), results)

    @Test
    fun otherHostsArePassedThrough() {
        val request = Request.Builder().url("https://poipiku.com/").build()
        val chain = chain(request, listOf(okResponse(request)))

        interceptor().intercept(chain)

        assertEquals(listOf("poipiku.com"), chain.hosts)
    }

    @Test
    fun bypassHeaderSkipsRelayAndIsStripped() {
        settings.setImageRouteMode(ImageRouteMode.RELAY)
        val request = cdnRequest.newBuilder()
            .header(ImageRelayInterceptor.BYPASS_HEADER, "1")
            .build()
        val chain = chain(request, listOf(okResponse(request)))

        interceptor().intercept(chain)

        assertEquals(listOf(ImageRelayInterceptor.CDN_HOST), chain.hosts)
        assertNull(chain.proceeded.single().header(ImageRelayInterceptor.BYPASS_HEADER))
    }

    @Test
    fun relayModeStartsFromTheFirstRelay() {
        settings.setImageRouteMode(ImageRouteMode.RELAY)
        val chain = chain(results = listOf(okResponse(cdnRequest)))

        interceptor().intercept(chain)

        assertEquals(listOf("pic-relay.cyou"), chain.hosts)
    }

    @Test
    fun failedRelayIsRetriedForTheNextImage() {
        settings.setImageRouteMode(ImageRouteMode.RELAY)
        val interceptor = interceptor(hosts = listOf("relay-a"))

        val first = chain(results = listOf(IOException("boom"), okResponse(cdnRequest)))
        interceptor.intercept(first)
        assertEquals(listOf("relay-a", ImageRelayInterceptor.CDN_HOST), first.hosts)

        // 直连不可用时中转是唯一出路：一次失败不能把它挡在候选外
        val second = chain(results = listOf(okResponse(cdnRequest)))
        interceptor.intercept(second)
        assertEquals(listOf("relay-a"), second.hosts)
    }

    @Test
    fun successfulRelayIsPreferredForTheNextImage() {
        settings.setImageRouteMode(ImageRouteMode.RELAY)
        val interceptor = interceptor()

        val first = chain(results = listOf(IOException("boom"), okResponse(cdnRequest)))
        interceptor.intercept(first)
        assertEquals(listOf("pic-relay.cyou", "piku-img.pages.dev"), first.hosts)

        val second = chain(results = listOf(okResponse(cdnRequest)))
        interceptor.intercept(second)
        assertEquals(listOf("piku-img.pages.dev"), second.hosts)
    }

    @Test
    fun cancelledAttemptDoesNotCoolDownTheRelay() {
        settings.setImageRouteMode(ImageRouteMode.RELAY)
        val interceptor = interceptor(hosts = listOf("relay-a"))

        val cancelled = chain(results = listOf(IOException("timeout"), okResponse(cdnRequest)), canceled = true)
        interceptor.intercept(cancelled)
        assertEquals(listOf("relay-a", ImageRelayInterceptor.CDN_HOST), cancelled.hosts)

        // 取消（整体超时）不算中继故障：下一张图仍然先试它
        val next = chain(results = listOf(okResponse(cdnRequest)))
        interceptor.intercept(next)
        assertEquals(listOf("relay-a"), next.hosts)
    }

    @Test
    fun nonSuccessResponseMovesOnWithoutCoolingDown() {
        settings.setImageRouteMode(ImageRouteMode.RELAY)
        val interceptor = interceptor()

        // 第一条返回 404，第二条连接失败
        val first = chain(
            results = listOf(
                okResponse(cdnRequest, 404),
                IOException("boom"),
                okResponse(cdnRequest),
            ),
        )
        interceptor.intercept(first)
        assertEquals(
            listOf("pic-relay.cyou", "piku-img.pages.dev", ImageRelayInterceptor.CDN_HOST),
            first.hosts,
        )

        // 404/502 只换这张图的下一家，不记在这条线路上：它仍然排在最前；
        // 连接失败的那条也还在候选里，只是这一轮排在后面
        val second = chain(results = listOf(okResponse(cdnRequest)))
        interceptor.intercept(second)
        assertEquals(listOf("pic-relay.cyou"), second.hosts)
    }

    @Test
    fun directModeFallsBackToRelayWhenDirectFails() {
        settings.setImageRouteMode(ImageRouteMode.DIRECT)
        val chain = chain(results = listOf(IOException("boom"), okResponse(cdnRequest)))

        interceptor().intercept(chain)

        assertEquals(listOf(ImageRelayInterceptor.CDN_HOST, "pic-relay.cyou"), chain.hosts)
    }

    @Test
    fun directModeSuccessDoesNotTouchRelays() {
        settings.setImageRouteMode(ImageRouteMode.DIRECT)
        val chain = chain(results = listOf(okResponse(cdnRequest)))

        interceptor().intercept(chain)

        assertEquals(listOf(ImageRelayInterceptor.CDN_HOST), chain.hosts)
    }
}
