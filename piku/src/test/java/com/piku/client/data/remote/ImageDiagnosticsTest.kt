package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageDiagnosticsTest {

    private var now = 1_700_000_000_000L
    private val diagnostics = ImageDiagnostics(NetworkRuntime(now = { now }, sleeper = {}))

    private fun attempt(
        host: String,
        outcome: ImageDiagnostics.Outcome,
        elapsedMs: Long = 100,
    ) = diagnostics.recordAttempt(
        host = host,
        path = "/img/x_360.jpg",
        outcome = outcome,
        detail = "",
        elapsedMs = elapsedMs,
        relay = host != ImageUpstream.POIPIKU.host,
    )

    @Test
    fun countsSuccessFailureAndCancellation() {
        attempt(ImageUpstream.POIPIKU.host, ImageDiagnostics.Outcome.OK)
        attempt("pic-relay.cyou", ImageDiagnostics.Outcome.HANDSHAKE_FAILED)
        attempt("pic-relay.cyou", ImageDiagnostics.Outcome.CANCELLED)

        val summary = diagnostics.summary()

        assertTrue(summary.first().contains("请求 3 次：成功 1  失败 1  取消 1"))
        assertTrue(summary.any { it.contains("握手/连接被重置（常见于 SNI 阻断）  1") })
        assertTrue(summary.any { it.startsWith("线路：直连 1  中转 2") && it.contains("pic-relay.cyou 2") })
    }

    @Test
    fun failureLinesCarryRouteOutcomeAndElapsed() {
        attempt("pic-relay.cyou", ImageDiagnostics.Outcome.HANDSHAKE_FAILED, elapsedMs = 218)

        val line = diagnostics.summary().last()

        assertTrue(line.contains("中转 pic-relay.cyou/img/x_360.jpg"))
        assertTrue(line.contains("218ms"))
    }

    @Test
    fun noRequestsIsStatedExplicitly() {
        assertEquals(listOf("本次进程还没有图片网络请求"), diagnostics.summary())
    }

    @Test
    fun clearDropsEverything() {
        attempt(ImageUpstream.POIPIKU.host, ImageDiagnostics.Outcome.OK)

        diagnostics.clear()

        assertEquals(listOf("本次进程还没有图片网络请求"), diagnostics.summary())
    }
}
