package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

class RetryPolicyTest {

    private val policy = RetryPolicy(jitterMs = { 0L })

    private fun retry(decision: RetryPolicy.Decision): RetryPolicy.Decision.Retry {
        assertTrue("expected retry but was $decision", decision is RetryPolicy.Decision.Retry)
        return decision as RetryPolicy.Decision.Retry
    }

    @Test
    fun ioFailureRetriesWithBackoffAndSwitchesAddress() {
        val decision = retry(
            policy.ioFailure(IOException("boom"), retryableMethod = true, attempts = RetryPolicy.Attempts()),
        )

        assertEquals(1, decision.attempts.io)
        assertEquals(300L, decision.delayMs)
        assertTrue(decision.replaceAddress)
    }

    @Test
    fun ioBackoffGrowsUntilTheBudgetRunsOut() {
        var attempts = RetryPolicy.Attempts()
        val delays = mutableListOf<Long>()
        while (true) {
            val decision = policy.ioFailure(IOException("boom"), retryableMethod = true, attempts)
            if (decision !is RetryPolicy.Decision.Retry) break
            attempts = decision.attempts
            delays += decision.delayMs
        }

        assertEquals(listOf(300L, 600L), delays)
    }

    @Test
    fun readTimeoutIsRetriedOnlyOnce() {
        val first = retry(
            policy.ioFailure(SocketTimeoutException(), retryableMethod = true, attempts = RetryPolicy.Attempts()),
        )

        assertEquals(1, first.attempts.timeout)

        assertEquals(
            RetryPolicy.Decision.GiveUp,
            policy.ioFailure(SocketTimeoutException(), retryableMethod = true, attempts = first.attempts),
        )
    }

    @Test
    fun timeoutsAndPlainIoHaveSeparateBudgets() {
        val attempts = RetryPolicy.Attempts(io = 1, timeout = 1)

        val decision = retry(policy.ioFailure(IOException("boom"), retryableMethod = true, attempts = attempts))

        assertEquals(2, decision.attempts.io)
        assertEquals(1, decision.attempts.timeout)
    }

    @Test
    fun postIsRetriedOnlyWhenItNeverReachedTheServer() {
        val attempts = RetryPolicy.Attempts()

        // 连接没建立起来 / 握手没过：确定没送达，重试安全
        assertTrue(policy.ioFailure(UnknownHostException(), false, attempts) is RetryPolicy.Decision.Retry)
        assertTrue(policy.ioFailure(ConnectException(), false, attempts) is RetryPolicy.Decision.Retry)
        assertTrue(policy.ioFailure(SSLHandshakeException("x"), false, attempts) is RetryPolicy.Decision.Retry)

        // 可能已经送达：读超时、宽泛的 SSLException、写途中的 NoRouteToHost
        assertEquals(RetryPolicy.Decision.GiveUp, policy.ioFailure(SocketTimeoutException(), false, attempts))
        assertEquals(RetryPolicy.Decision.GiveUp, policy.ioFailure(SSLException("x"), false, attempts))
        assertEquals(RetryPolicy.Decision.GiveUp, policy.ioFailure(NoRouteToHostException(), false, attempts))
    }

    @Test
    fun getStillRetriesThoseWithdrawalsThatPostRefuses() {
        assertEquals(
            RetryPolicy.Decision.GiveUp,
            policy.ioFailure(NoRouteToHostException(), retryableMethod = false, attempts = RetryPolicy.Attempts()),
        )
        assertTrue(
            policy.ioFailure(
                NoRouteToHostException(),
                retryableMethod = true,
                attempts = RetryPolicy.Attempts(),
            ) is RetryPolicy.Decision.Retry,
        )
    }

    @Test
    fun httpRetryCovers429And5xxForGetOnly() {
        val attempts = RetryPolicy.Attempts()

        assertEquals(1_000L, retry(policy.httpFailure(500, true, attempts)).delayMs)
        assertTrue(policy.httpFailure(429, true, attempts) is RetryPolicy.Decision.Retry)
        assertTrue(policy.httpFailure(503, true, attempts) is RetryPolicy.Decision.Retry)

        assertEquals(RetryPolicy.Decision.GiveUp, policy.httpFailure(404, true, attempts))
        assertEquals(RetryPolicy.Decision.GiveUp, policy.httpFailure(500, retryableMethod = false, attempts))
    }

    @Test
    fun httpRetryKeepsTheSameAddress() {
        val decision = retry(policy.httpFailure(500, true, RetryPolicy.Attempts()))

        assertFalse("服务端已经应答，不该换 IP", decision.replaceAddress)
    }

    @Test
    fun httpRetriesStopAtMaxAttempts() {
        assertEquals(
            RetryPolicy.Decision.GiveUp,
            policy.httpFailure(503, true, RetryPolicy.Attempts(http = 2)),
        )
    }
}
