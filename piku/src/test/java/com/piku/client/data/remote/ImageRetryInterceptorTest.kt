package com.piku.client.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
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
}
