package com.piku.client.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
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
        NetworkRuntime(now = { 1_000_000L }, sleeper = {}),
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

    @Test
    fun alreadyOnTheRelayIsNotASecondSwitch() {
        val controller = controller()
        switchRouteOnTransferFailure(ImageUpstream.PIXIV, InterruptedIOException("timeout"), controller)

        assertFalse(
            switchRouteOnTransferFailure(
                ImageUpstream.PIXIV,
                InterruptedIOException("timeout"),
                controller,
            ),
        )
    }
}
