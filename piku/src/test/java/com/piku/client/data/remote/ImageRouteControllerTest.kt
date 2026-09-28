package com.piku.client.data.remote

import com.piku.client.data.local.InMemorySharedPreferences
import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.ImageRouteMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageRouteControllerTest {

    private var now = 1_000_000L
    private val runtime = NetworkRuntime(now = { now }, sleeper = {})
    private val prefs = InMemorySharedPreferences()
    private val settings = SettingsRepository(InMemorySharedPreferences())
    private val controller = ImageRouteController(settings, prefs, runtime)

    @Test
    fun autoModeStartsDirectAndFollowsTheProbe() {
        assertFalse(controller.useRelay)

        controller.applyProbe(directOk = false)

        assertTrue(controller.useRelay)
    }

    @Test
    fun flipIsThrottledWithinTheCooldown() {
        controller.applyProbe(directOk = false)
        assertTrue(controller.useRelay)

        // 冷却期内不翻：直连与中转同时全挂时来回翻转会打满两条链路
        controller.applyProbe(directOk = true)
        assertTrue(controller.useRelay)

        now += 20_000
        controller.applyProbe(directOk = true)
        assertFalse(controller.useRelay)
    }

    @Test
    fun decisionRecordsWhyItFlipped() {
        controller.applyProbe(
            ImageProbeResult(ok = false, elapsedMs = 312, statusCode = null, error = "timeout", atMillis = now),
        )

        val decision = controller.lastDecision!!
        assertTrue(decision.relay)
        assertTrue(decision.applied)
        assertTrue(decision.reason.contains("直连不可用"))
        assertEquals(312L, controller.lastProbe!!.elapsedMs)
    }

    @Test
    fun decisionRecordsWhenTheFlipWasThrottled() {
        controller.applyProbe(ImageProbeResult(false, 1, null, null, now))
        controller.applyProbe(ImageProbeResult(true, 1, 200, null, now))

        val decision = controller.lastDecision!!
        assertFalse(decision.applied)
        assertTrue(decision.reason.contains("冷却中"))
    }

    @Test
    fun probeFailurePinsRelayEvenWhenRelaysAlsoFail() {
        controller.applyProbe(directOk = false)
        assertTrue(controller.useRelay)

        // 冷却期过后中转也失败：直连探测已判死，切回去必然还是失败，不许翻
        now += 20_000
        controller.markRelayFailed()

        assertTrue("直连已判定不可用时不该切回直连", controller.useRelay)
        val decision = controller.lastDecision!!
        assertTrue(decision.applied)
        assertTrue(decision.reason.contains("直连已判定不可用"))
    }

    @Test
    fun directRequestFailurePinsTheRelayRouteToo() {
        controller.markDirectFailed()
        assertTrue(controller.useRelay)

        now += 20_000
        controller.markRelayFailed()

        assertTrue("真实请求证明直连不通后，中转失败也不切回", controller.useRelay)
    }

    @Test
    fun probeSayingDirectWorksBringsItBack() {
        controller.applyProbe(directOk = false)
        now += 20_000
        controller.markRelayFailed()
        assertTrue(controller.useRelay)

        // 探测是解除"直连不可用"的出口：换了网络要能回到直连
        now += 20_000
        controller.applyProbe(directOk = true)

        assertFalse(controller.useRelay)
    }

    @Test
    fun manualModesIgnoreFallbackSignals() {
        settings.setImageRouteMode(ImageRouteMode.DIRECT)
        controller.markRelayFailed()
        settings.setImageRouteMode(ImageRouteMode.RELAY)
        controller.markDirectFailed()
        settings.setImageRouteMode(ImageRouteMode.AUTO)

        assertFalse("手动模式的失败信号不该改掉自动选择", controller.useRelay)
    }

    @Test
    fun directAndRelayAreForcedRegardlessOfProbe() {
        settings.setImageRouteMode(ImageRouteMode.RELAY)
        assertTrue(controller.useRelay)

        settings.setImageRouteMode(ImageRouteMode.DIRECT)
        assertFalse(controller.useRelay)
    }

    @Test
    fun probeOnlyRunsInAutoMode() {
        assertTrue(controller.shouldRunProbe())

        settings.setImageRouteMode(ImageRouteMode.DIRECT)
        assertFalse(controller.shouldRunProbe())

        settings.setImageRouteMode(ImageRouteMode.RELAY)
        assertFalse(controller.shouldRunProbe())
    }

    @Test
    fun autoChoiceSurvivesRestart() {
        controller.applyProbe(directOk = false)

        val restored = ImageRouteController(settings, prefs, runtime)

        assertTrue(restored.useRelay)
    }
}
