package com.piku.client.data.remote

import com.piku.client.data.local.InMemorySharedPreferences
import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.ImageRouteMode
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ImageRelayInterceptorTest {

    private var now = 0L
    private val runtime = NetworkRuntime(now = { now }, monotonicNow = { now }, sleeper = {})
    private val prefs = InMemorySharedPreferences()
    private val settings = SettingsRepository(InMemorySharedPreferences())
    private val controller = ImageRouteController(settings, prefs, runtime)

    private val cdnRequest = Request.Builder()
        .url("https://${ImageUpstream.POIPIKU.host}/assets/img/a.png")
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

        assertEquals(listOf(ImageUpstream.POIPIKU.host), chain.hosts)
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
        assertEquals(listOf("relay-a", ImageUpstream.POIPIKU.host), first.hosts)

        // 直连不可用时中转是唯一出路：一次失败不能把它挡在候选外
        val second = chain(results = listOf(okResponse(cdnRequest)))
        interceptor.intercept(second)
        assertEquals(listOf("relay-a"), second.hosts)
    }

    @Test
    fun successfulRelayIsPreferredForTheNextImage() {
        settings.setImageRouteMode(ImageRouteMode.RELAY)
        // 候选写死在测试里：生产清单（自建域名）只剩一条，但"多条中继择优"的行为仍要守住
        val interceptor = interceptor(hosts = listOf("relay-a", "relay-b"))

        val first = chain(results = listOf(IOException("boom"), okResponse(cdnRequest)))
        interceptor.intercept(first)
        assertEquals(listOf("relay-a", "relay-b"), first.hosts)

        val second = chain(results = listOf(okResponse(cdnRequest)))
        interceptor.intercept(second)
        assertEquals(listOf("relay-b"), second.hosts)
    }

    @Test
    fun cancelledAttemptDoesNotCoolDownTheRelay() {
        settings.setImageRouteMode(ImageRouteMode.RELAY)
        val interceptor = interceptor(hosts = listOf("relay-a"))

        val cancelled = chain(results = listOf(IOException("timeout"), okResponse(cdnRequest)), canceled = true)
        interceptor.intercept(cancelled)
        assertEquals(listOf("relay-a", ImageUpstream.POIPIKU.host), cancelled.hosts)

        // 取消（整体超时）不算中继故障：下一张图仍然先试它
        val next = chain(results = listOf(okResponse(cdnRequest)))
        interceptor.intercept(next)
        assertEquals(listOf("relay-a"), next.hosts)
    }

    @Test
    fun nonSuccessResponseMovesOnWithoutCoolingDown() {
        settings.setImageRouteMode(ImageRouteMode.RELAY)
        val interceptor = interceptor(hosts = listOf("relay-a", "relay-b"))

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
            listOf("relay-a", "relay-b", ImageUpstream.POIPIKU.host),
            first.hosts,
        )

        // 404/502 只换这张图的下一家，不记在这条线路上：它仍然排在最前；
        // 连接失败的那条也还在候选里，只是这一轮排在后面
        val second = chain(results = listOf(okResponse(cdnRequest)))
        interceptor.intercept(second)
        assertEquals(listOf("relay-a"), second.hosts)
    }

    @Test
    fun directModeFallsBackToRelayWhenDirectFails() {
        settings.setImageRouteMode(ImageRouteMode.DIRECT)
        val chain = chain(results = listOf(IOException("boom"), okResponse(cdnRequest)))

        interceptor().intercept(chain)

        assertEquals(listOf(ImageUpstream.POIPIKU.host, "pic-relay.cyou"), chain.hosts)
    }

    @Test
    fun directModeSuccessDoesNotTouchRelays() {
        settings.setImageRouteMode(ImageRouteMode.DIRECT)
        val chain = chain(results = listOf(okResponse(cdnRequest)))

        interceptor().intercept(chain)

        assertEquals(listOf(ImageUpstream.POIPIKU.host), chain.hosts)
    }

    @Test
    fun pixivImageKeepsItsPathUnderTheRelayPrefix() {
        settings.setImageRouteMode(ImageRouteMode.RELAY)
        val pixiv = Request.Builder()
            .url("https://${ImageUpstream.PIXIV.host}/img-master/img/a/b/c_p0_master1200.jpg")
            .build()
        val chain = chain(request = pixiv, results = listOf(okResponse(pixiv)))

        interceptor().intercept(chain)

        assertEquals(listOf("pic-relay.cyou"), chain.hosts)
        // 中继按 /pximg 前缀回源 i.pximg.net，路径不能丢
        assertEquals(
            "/pximg/img-master/img/a/b/c_p0_master1200.jpg",
            chain.proceeded.single().url.encodedPath,
        )
    }

    @Test
    fun poipikuImageKeepsItsOriginalPath() {
        settings.setImageRouteMode(ImageRouteMode.RELAY)
        val chain = chain(results = listOf(okResponse(cdnRequest)))

        interceptor().intercept(chain)

        assertEquals(listOf("pic-relay.cyou"), chain.hosts)
        assertEquals("/assets/img/a.png", chain.proceeded.single().url.encodedPath)
    }

    @Test
    fun pixivImageStartsDirectSoGoodNetworksDoNotSpendTheRelay() {
        val pixiv = Request.Builder()
            .url("https://${ImageUpstream.PIXIV.host}/img-master/img/a/b/c_p0_master1200.jpg")
            .build()
        val chain = chain(request = pixiv, results = listOf(okResponse(pixiv)))

        interceptor().intercept(chain)

        assertEquals(listOf(ImageUpstream.PIXIV.host), chain.hosts)
    }

    @Test
    fun pixivDirectFailureSwitchesItToTheRelayForGood() {
        // 翻转有 20s 冷却，起点挪开一点，免得被冷却拦住（那是另一条测试管的）
        now = 100_000L
        val pixiv = Request.Builder()
            .url("https://${ImageUpstream.PIXIV.host}/img-master/img/a/b/c_p0_master1200.jpg")
            .build()
        val controller = ImageRouteController(settings, prefs, runtime)
        val interceptor = ImageRelayInterceptor(controller)

        // 直连撞超时（大图传不完的典型样子）→ 这次回落中继
        val first = chain(request = pixiv, results = listOf(IOException("timeout"), okResponse(pixiv)))
        interceptor.intercept(first)
        assertEquals(listOf(ImageUpstream.PIXIV.host, "pic-relay.cyou"), first.hosts)

        // 下一张直接从中继开始，不再白等一次 15 秒
        val second = chain(request = pixiv, results = listOf(okResponse(pixiv)))
        interceptor.intercept(second)
        assertEquals(listOf("pic-relay.cyou"), second.hosts)
    }

    /** AUTO 模式 + 上会话粘在中继（持久化），directDown 已随重启归零；prefs 局部建，避免污染其他用例 */
    private fun stickyRelayInterceptor(): Triple<ImageRelayInterceptor, ImageRouteController, InMemorySharedPreferences> {
        now = 100_000L
        val stickyPrefs = InMemorySharedPreferences().apply {
            edit().putBoolean("image_route_auto_relay", true).apply()
        }
        val settings = SettingsRepository(InMemorySharedPreferences()).apply {
            setImageRouteMode(ImageRouteMode.AUTO)
        }
        val controller = ImageRouteController(settings, stickyPrefs, runtime)
        return Triple(ImageRelayInterceptor(controller), controller, stickyPrefs)
    }

    @Test
    fun relay404DoesNotFlipTheStickyAutoRoute() {
        val (interceptor, controller, stickyPrefs) = stickyRelayInterceptor()

        // 中继正常应答 404（作品已删除），直连是好的：404 是"图没了"，不是"中继坏了"
        val first = chain(results = listOf(okResponse(cdnRequest, 404), okResponse(cdnRequest)))
        interceptor.intercept(first)

        assertEquals(listOf("pic-relay.cyou", ImageUpstream.POIPIKU.host), first.hosts)
        assertTrue("404 不该翻转粘住的线路", controller.useRelay(ImageUpstream.POIPIKU))
        assertTrue("也不该持久化翻转", stickyPrefs.getBoolean("image_route_auto_relay", false))
    }

    @Test
    fun cancelledRelayAttemptDoesNotFlipTheStickyAutoRoute() {
        val (interceptor, controller, stickyPrefs) = stickyRelayInterceptor()

        // 划走/整体超时导致的取消同样不是中继的错
        val first = chain(results = listOf(IOException("timeout"), okResponse(cdnRequest)), canceled = true)
        interceptor.intercept(first)

        assertEquals(listOf("pic-relay.cyou", ImageUpstream.POIPIKU.host), first.hosts)
        assertTrue("取消不该翻转粘住的线路", controller.useRelay(ImageUpstream.POIPIKU))
        assertTrue(stickyPrefs.getBoolean("image_route_auto_relay", false))
    }

    @Test
    fun allRelaysUnreachableStillFlipsTheStickyAutoRoute() {
        val (interceptor, controller, stickyPrefs) = stickyRelayInterceptor()

        // 中继真的连不上才该翻：翻完落直连把这张图接完
        val first = chain(results = listOf(IOException("boom"), okResponse(cdnRequest)))
        interceptor.intercept(first)

        assertEquals(listOf("pic-relay.cyou", ImageUpstream.POIPIKU.host), first.hosts)
        assertFalse("中继全断：该翻去直连", controller.useRelay(ImageUpstream.POIPIKU))
        assertFalse("翻转要持久化，重启后别再撞一次", stickyPrefs.getBoolean("image_route_auto_relay", true))
    }
}
