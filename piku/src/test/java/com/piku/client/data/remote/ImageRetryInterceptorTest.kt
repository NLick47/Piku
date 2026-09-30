package com.piku.client.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException

class ImageRetryInterceptorTest {

    private val policy = ImageRetryPolicy()

    @Test
    fun transportFailureIsRetriedThreeTimesThenHandedToCoil() = runBlocking {
        var fetches = 0
        val attempts = mutableListOf<Int>()
        val waits = mutableListOf<Long>()

        val result = retryFetch(
            policy = policy,
            fetch = {
                fetches++
                SocketTimeoutException("connect timed out")
            },
            failureOf = { it },
            onRetry = { _, attempt, delayMs ->
                attempts += attempt
                waits += delayMs
            },
            sleep = {},
        )

        // 首次 + 3 次重试：一张图最多取 4 次，之后把最后一次的失败原样交给 Coil
        assertEquals(4, fetches)
        assertEquals(listOf(1, 2, 3), attempts)
        assertEquals(3, waits.size)
        assertTrue(result is SocketTimeoutException)
    }

    @Test
    fun laterAttemptCanSucceed() = runBlocking {
        var fetches = 0

        val result = retryFetch(
            policy = policy,
            fetch = {
                fetches++
                if (fetches < 3) IOException("reset") else "loaded"
            },
            failureOf = { it as? IOException },
            sleep = {},
        )

        assertEquals("loaded", result)
        assertEquals(3, fetches)
    }

    @Test
    fun nonTransportFailureIsReturnedWithoutRetrying() = runBlocking {
        var fetches = 0

        val result = retryFetch(
            policy = policy,
            fetch = {
                fetches++
                CancellationException("page scrolled away")
            },
            failureOf = { it },
            sleep = {},
        )

        assertEquals(1, fetches)
        assertTrue(result is CancellationException)
    }

    // 直连取大图失败的现场：读 body 时被 callTimeout 掐掉，异常出在 Coil 侧，
    // OkHttp 拦截器链看不到 —— 所以切换必须挂在这一层
    private val prefs = com.piku.client.data.local.InMemorySharedPreferences()
    private val settings =
        com.piku.client.data.local.SettingsRepository(com.piku.client.data.local.InMemorySharedPreferences())

    private fun controller() = ImageRouteController(
        settings,
        prefs,
        NetworkRuntime(now = { 1_000_000L }, monotonicNow = { 1_000_000L }, sleeper = {}),
    )

    @Test
    fun readTimeoutOnAManagedUpstreamFlipsItToTheRelay() {
        val controller = controller()

        val switched = switchRouteOnTransferFailure(
            ImageUpstream.PIXIV,
            InterruptedIOException("timeout"),
            controller,
        )

        assertTrue(switched)
        assertTrue(controller.useRelay(ImageUpstream.PIXIV))
        assertFalse("另一条上游不该受影响", controller.useRelay(ImageUpstream.POIPIKU))
    }

    @Test
    fun httpFailureAndCancellationDoNotFlipTheRoute() {
        val controller = controller()

        assertFalse(switchRouteOnTransferFailure(ImageUpstream.PIXIV, IOException("HTTP 500"), controller))
        assertFalse(switchRouteOnTransferFailure(ImageUpstream.PIXIV, CancellationException(), controller))
        assertFalse(controller.useRelay(ImageUpstream.PIXIV))
    }

    @Test
    fun unmanagedHostAndMissingControllerAreIgnored() {
        val controller = controller()

        assertFalse(switchRouteOnTransferFailure(null, InterruptedIOException("timeout"), controller))
        assertFalse(
            switchRouteOnTransferFailure(
                ImageUpstream.PIXIV,
                InterruptedIOException("timeout"),
                null,
            ),
        )
    }

    /** 独立 prefs：这两个用例要写持久化的粘住状态，不能污染同文件其他用例 */
    private fun stickyRelayController(): Pair<ImageRouteController, com.piku.client.data.local.InMemorySharedPreferences> {
        val prefs = com.piku.client.data.local.InMemorySharedPreferences()
        prefs.edit().putBoolean("image_route_auto_relay_pixiv", true).apply()
        return ImageRouteController(
            settings,
            prefs,
            NetworkRuntime(now = { 1_000_000L }, monotonicNow = { 1_000_000L }, sleeper = {}),
        ) to prefs
    }

    @Test
    fun relayBodyFailureFlipsBackToDirect() {
        // 上会话粘在中继（持久化），directDown 未置位：中继传一半断流时允许翻回直连
        val (controller, prefs) = stickyRelayController()
        assertTrue(controller.useRelay(ImageUpstream.PIXIV))

        val flipped = switchRouteOnTransferFailure(
            ImageUpstream.PIXIV,
            ProtocolException("unexpected end of stream"),
            controller,
        )

        assertTrue(flipped)
        assertFalse(controller.useRelay(ImageUpstream.PIXIV))
        assertFalse("翻回直连也要持久化", prefs.getBoolean("image_route_auto_relay_pixiv", true))
    }

    @Test
    fun doubleFailureWithinTheFlipCooldownDoesNotThrash() {
        val (controller, _) = stickyRelayController()

        // 中继断流 → 翻直连；直连随即也超时：冷却期内不许翻回中继打摆
        assertTrue(switchRouteOnTransferFailure(ImageUpstream.PIXIV, ProtocolException("boom"), controller))
        assertFalse(switchRouteOnTransferFailure(ImageUpstream.PIXIV, InterruptedIOException("timeout"), controller))
        assertFalse(controller.useRelay(ImageUpstream.PIXIV))
    }

    @Test
    fun truncatedBodyCountsAsATransferFailure() {
        // okhttp 对 body 中途截断抛的是 ProtocolException：这是传输问题，不能漏判
        assertTrue(isTransferFailure(ProtocolException("unexpected end of stream")))
        assertTrue(isTransferFailure(IOException(ProtocolException("unexpected end of stream"))))
    }
}
