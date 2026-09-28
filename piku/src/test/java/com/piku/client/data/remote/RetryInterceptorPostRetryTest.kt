package com.piku.client.data.remote

import com.piku.client.data.local.InMemorySharedPreferences
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

class RetryInterceptorPostRetryTest {

    private val dns = DoHDns(InMemorySharedPreferences())
    private val interceptor = RetryInterceptor(
        dns,
        runtime = NetworkRuntime(now = { 0L }, sleeper = {}),
    )

    private val post = Request.Builder()
        .url("https://poipiku.com/f/12345")
        .post("a=1".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
        .build()

    private val get = Request.Builder()
        .url("https://poipiku.com/f/12345")
        .build()

    @Test
    fun postIsRetriedWhenTheConnectionNeverOpened() {
        val chain = FakeChain(post, FakeCall(post), listOf(ConnectException("refused"), okResponse(post)))

        val result = interceptor.intercept(chain)

        assertEquals(200, result.code)
        assertEquals(2, chain.proceeded.size)
    }

    @Test
    fun postIsRetriedWhenTheHandshakeFailed() {
        val chain = FakeChain(
            post,
            FakeCall(post),
            listOf(SSLHandshakeException("handshake"), okResponse(post)),
        )

        assertEquals(200, interceptor.intercept(chain).code)
        assertEquals(2, chain.proceeded.size)
    }

    @Test
    fun postIsNotRetriedAfterAReadTimeout() {
        val chain = FakeChain(post, FakeCall(post), listOf(SocketTimeoutException("timeout"), okResponse(post)))

        assertThrows(SocketTimeoutException::class.java) { interceptor.intercept(chain) }
        assertEquals(1, chain.proceeded.size)
    }

    @Test
    fun postIsNotRetriedWhenTheHostIsUnreachable() {
        // 同一个 errno 也可能在写入途中抛出：重试上传有重复建作品的风险
        val chain = FakeChain(post, FakeCall(post), listOf(NoRouteToHostException("unreachable"), okResponse(post)))

        assertThrows(NoRouteToHostException::class.java) { interceptor.intercept(chain) }
        assertEquals(1, chain.proceeded.size)
    }

    @Test
    fun getStillRetriesThoseWithdrawalsThatPostRefuses() {
        val chain = FakeChain(get, FakeCall(get), listOf(NoRouteToHostException("unreachable"), okResponse(get)))

        assertEquals(200, interceptor.intercept(chain).code)
        assertEquals(2, chain.proceeded.size)
    }

    @Test
    fun postIsNeverRetriedOnHttpStatusEvenFor5xx() {
        val chain = FakeChain(post, FakeCall(post), listOf(okResponse(post, 500)))

        val result = interceptor.intercept(chain)

        assertEquals(500, result.code)
        assertEquals(1, chain.proceeded.size)
    }

    @Test
    fun postGivesUpAfterTheIoBudgetIsSpent() {
        val chain = FakeChain(
            post,
            FakeCall(post),
            listOf(ConnectException("a"), ConnectException("b"), ConnectException("c"), okResponse(post)),
        )

        assertThrows(IOException::class.java) { interceptor.intercept(chain) }
        assertEquals(3, chain.proceeded.size)
    }
}
