package com.piku.client.data.remote

import coil3.network.HttpException
import coil3.network.NetworkResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException

class ImageRetryPolicyTest {

    private val policy = ImageRetryPolicy()

    @Test
    fun waitsGrowWithEachAttemptAndStopAtTheCap() {
        // 默认退避 500 / 1500 / 3000，各带一点抖动
        assertTrue(policy.nextRetryMs(IOException("reset"), retries = 0) in 500L..620L)
        assertTrue(policy.nextRetryMs(IOException("reset"), retries = 1) in 1_500L..1_620L)
        assertTrue(policy.nextRetryMs(IOException("reset"), retries = 2) in 3_000L..3_120L)
        // 三次之后不再重试
        assertEquals(null, policy.nextRetryMs(IOException("reset"), retries = 3))
    }

    @Test
    fun transportFailuresAreWorthRetrying() {
        assertTrue(policy.isTransportFailure(SocketTimeoutException("connect timed out")))
        assertTrue(policy.isTransportFailure(IOException("Connection reset by peer")))

        // 引擎可能把底层 IOException 包一层：判断要看完整 cause 链
        val wrapped = RuntimeException("fetch failed").apply { initCause(IOException("Connection reset")) }
        assertTrue(policy.isTransportFailure(wrapped))
    }

    @Test
    fun httpErrorsAndCancellationsAreNotRetried() {
        // 404/502 是服务端的答复，再取几次都一样
        assertFalse(policy.isTransportFailure(httpError(404)))
        assertFalse(policy.isTransportFailure(httpError(502)))
        // 取消是页面滑走，不是线路故障
        assertFalse(policy.isTransportFailure(CancellationException("disposed")))
        assertFalse(policy.isTransportFailure(null))
    }

    @Test
    fun httpErrorWrappedInAnotherFailureIsStillNotRetried() {
        val wrapped = RuntimeException("fetch failed").apply { initCause(httpError(503)) }

        assertFalse(policy.isTransportFailure(wrapped))
    }

    @Test
    fun transportFailureNestedUnderAnHttpErrorIsNotRetried() {
        // 服务端的答复里可能挂着底层的读取失败：那是 HTTP 错误，不该再来一遍
        val httpError = httpError(502).apply { initCause(IOException("unexpected end of stream")) }

        assertFalse(policy.isTransportFailure(httpError))
    }

    @Test
    fun transportFailureNestedUnderACancellationIsNotRetried() {
        // 取消时抛出的是"套着 IOException 的取消"：页面都滑走了，别再取
        val cancelled = CancellationException("disposed").apply { initCause(IOException("socket closed")) }

        assertFalse(policy.isTransportFailure(cancelled))
    }

    private fun httpError(code: Int) = HttpException(
        NetworkResponse(code = code, requestMillis = 0, responseMillis = 0),
    )
}
