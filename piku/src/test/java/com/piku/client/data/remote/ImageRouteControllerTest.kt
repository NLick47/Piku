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
        assertFalse(controller.useRelay(ImageUpstream.POIPIKU))

        controller.applyProbe(directOk = false)

        assertTrue(controller.useRelay(ImageUpstream.POIPIKU))
    }

    @Test
    fun flipIsThrottledWithinTheCooldown() {
        controller.applyProbe(directOk = false)
        assertTrue(controller.useRelay(ImageUpstream.POIPIKU))

        // 冷却期内不翻：直连与中转同时全挂时来回翻转会打满两条链路
        controller.applyProbe(directOk = true)
        assertTrue(controller.useRelay(ImageUpstream.POIPIKU))

        now += 20_000
        controller.applyProbe(directOk = true)
        assertFalse(controller.useRelay(ImageUpstream.POIPIKU))
    }

    @Test
    fun decisionRecordsWhyItFlipped() {
        controller.applyProbe(
            ImageProbeResult(ok = false, elapsedMs = 312, statusCode = null, error = "timeout", atMillis = now),
        )

        val decision = controller.lastDecision(ImageUpstream.POIPIKU)!!
        assertTrue(decision.relay)
        assertTrue(decision.applied)
        assertTrue(decision.reason.contains("直连不可用"))
        assertEquals(312L, controller.lastProbe!!.elapsedMs)
    }

    @Test
    fun decisionRecordsWhenTheFlipWasThrottled() {
        controller.applyProbe(ImageProbeResult(false, 1, null, null, now))
        controller.applyProbe(ImageProbeResult(true, 1, 200, null, now))

        val decision = controller.lastDecision(ImageUpstream.POIPIKU)!!
        assertFalse(decision.applied)
        assertTrue(decision.reason.contains("冷却中"))
    }

    @Test
    fun probeFailurePinsRelayEvenWhenRelaysAlsoFail() {
        controller.applyProbe(directOk = false)
        assertTrue(controller.useRelay(ImageUpstream.POIPIKU))

        // 冷却期过后中转也失败：直连探测已判死，切回去必然还是失败，不许翻
        now += 20_000
        controller.markRelayFailed(ImageUpstream.POIPIKU)

        assertTrue("直连已判定不可用时不该切回直连", controller.useRelay(ImageUpstream.POIPIKU))
        val decision = controller.lastDecision(ImageUpstream.POIPIKU)!!
        assertTrue(decision.applied)
        assertTrue(decision.reason.contains("直连已判定不可用"))
    }

    @Test
    fun directRequestFailurePinsTheRelayRouteToo() {
        controller.markDirectFailed(ImageUpstream.POIPIKU)
        assertTrue(controller.useRelay(ImageUpstream.POIPIKU))

        now += 20_000
        controller.markRelayFailed(ImageUpstream.POIPIKU)

        assertTrue("真实请求证明直连不通后，中转失败也不切回", controller.useRelay(ImageUpstream.POIPIKU))
    }

    @Test
    fun probeSayingDirectWorksBringsItBack() {
        controller.applyProbe(directOk = false)
        now += 20_000
        controller.markRelayFailed(ImageUpstream.POIPIKU)
        assertTrue(controller.useRelay(ImageUpstream.POIPIKU))

        // 探测是解除"直连不可用"的出口：换了网络要能回到直连
        now += 20_000
        controller.applyProbe(directOk = true)

        assertFalse(controller.useRelay(ImageUpstream.POIPIKU))
    }

    @Test
    fun manualModesIgnoreFallbackSignals() {
        settings.setImageRouteMode(ImageRouteMode.DIRECT)
        controller.markRelayFailed(ImageUpstream.POIPIKU)
        settings.setImageRouteMode(ImageRouteMode.RELAY)
        controller.markDirectFailed(ImageUpstream.POIPIKU)
        settings.setImageRouteMode(ImageRouteMode.AUTO)

        assertFalse("手动模式的失败信号不该改掉自动选择", controller.useRelay(ImageUpstream.POIPIKU))
    }

    @Test
    fun directAndRelayAreForcedRegardlessOfProbe() {
        settings.setImageRouteMode(ImageRouteMode.RELAY)
        assertTrue(controller.useRelay(ImageUpstream.POIPIKU))

        settings.setImageRouteMode(ImageRouteMode.DIRECT)
        assertFalse(controller.useRelay(ImageUpstream.POIPIKU))
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

        assertTrue(restored.useRelay(ImageUpstream.POIPIKU))
    }

    @Test
    fun theTwoUpstreamsKeepTheirOwnState() {
        // poipiku 直连失败切中继，不该把 pixiv 那条也带过去
        controller.markDirectFailed(ImageUpstream.POIPIKU)
        assertTrue(controller.useRelay(ImageUpstream.POIPIKU))
        assertFalse("另一条上游不该受影响", controller.useRelay(ImageUpstream.PIXIV))

        // pixiv 直连失败只影响自己
        controller.markDirectFailed(ImageUpstream.PIXIV)
        assertTrue(controller.useRelay(ImageUpstream.PIXIV))
    }

    @Test
    fun pixivStartsDirectSoGoodNetworksDoNotSpendTheRelay() {
        // 默认先直连：能直连的网络（境外/开梯子）一分中继资源都不该花
        assertFalse(controller.useRelay(ImageUpstream.PIXIV))
        assertFalse(controller.useRelay(ImageUpstream.POIPIKU))
    }

    @Test
    fun pixivDirectFailureSticksToTheRelayAcrossRestart() {
        controller.markDirectFailed(ImageUpstream.PIXIV)

        val restored = ImageRouteController(settings, prefs, runtime)

        assertTrue(restored.useRelay(ImageUpstream.PIXIV))
        assertFalse("poipiku 那条不受影响", restored.useRelay(ImageUpstream.POIPIKU))
    }

    @Test
    fun pixivProbeSayingDirectWorksBringsItBack() {
        controller.markDirectFailed(ImageUpstream.PIXIV)
        now += 20_000

        controller.applyProbe(ImageUpstream.PIXIV, directOk = true)

        assertFalse(controller.useRelay(ImageUpstream.PIXIV))
    }

    @Test
    fun poipikuProbeDoesNotTouchPixiv() {
        controller.markDirectFailed(ImageUpstream.PIXIV)
        now += 20_000

        controller.applyProbe(directOk = true)

        assertTrue("探的是 poipiku 直连，pixiv 的结论不该被改", controller.useRelay(ImageUpstream.PIXIV))
    }

    @Test
    fun fastDirectMeasurementKeepsTrafficOffTheRelay() {
        // 实测直连够快 → 直连；即便之前粘在中继上也要切回来（省中继）
        controller.markDirectFailed(ImageUpstream.PIXIV)
        assertTrue(controller.useRelay(ImageUpstream.PIXIV))
        now += 20_000

        controller.recordSpeed(ImageUpstream.PIXIV, relay = false, bytes = 1_048_576, elapsedMs = 2_000)

        assertFalse("实测 500KB/s，没理由再走中继", controller.useRelay(ImageUpstream.PIXIV))
    }

    @Test
    fun slowDirectMeasurementSwitchesToTheRelayWithoutWaitingForAFailure() {
        assertFalse(controller.useRelay(ImageUpstream.PIXIV))

        // 1MB 用了 20 秒 ≈ 52KB/s：这次侥幸传完了，下次大图必然撞超时
        controller.recordSpeed(ImageUpstream.PIXIV, relay = false, bytes = 1_048_576, elapsedMs = 20_000)
        controller.recordSpeed(ImageUpstream.PIXIV, relay = true, bytes = 1_048_576, elapsedMs = 4_000)

        assertTrue(controller.useRelay(ImageUpstream.PIXIV))
    }

    @Test
    fun unknownDirectSpeedFallsBackToTheStickyChoice() {
        assertFalse(controller.useRelay(ImageUpstream.PIXIV))

        // 只有中继的读数、直连没测过：不该凭它切走
        controller.recordSpeed(ImageUpstream.PIXIV, relay = true, bytes = 1_048_576, elapsedMs = 1_000)

        assertFalse(controller.useRelay(ImageUpstream.PIXIV))
    }

    @Test
    fun measurePerUpstream() {
        controller.recordSpeed(ImageUpstream.PIXIV, relay = false, bytes = 1_048_576, elapsedMs = 20_000)
        controller.recordSpeed(ImageUpstream.POIPIKU, relay = false, bytes = 1_048_576, elapsedMs = 1_000)

        assertEquals(52_428L, controller.speed(ImageUpstream.PIXIV).rate(relay = false))
        assertEquals(1_048_576L, controller.speed(ImageUpstream.POIPIKU).rate(relay = false))
    }

    @Test
    fun inlineFullImageNeedsAMeasurementThatFitsTheBudget() {
        // 没样本：不赌这 1MB，内联先用打底档（用户进查看器再取清晰档）
        assertFalse(controller.worthFullImageInline(ImageUpstream.PIXIV))
    }

    @Test
    fun inlineFullImageStaysOffWhenTheMeasuredRateIsSlow() {
        // 1MB 用了 8 秒 ≈ 131KB/s：预算内到不了，别让用户盯着图区等
        controller.recordSpeed(ImageUpstream.PIXIV, relay = false, bytes = 1_048_576, elapsedMs = 8_000)

        assertFalse(controller.worthFullImageInline(ImageUpstream.PIXIV))
    }

    @Test
    fun inlineFullImageGoesUpWhenTheMeasuredRateIsFast() {
        // 1MB 用了 1 秒 ≈ 1MB/s：点进来就取清晰档也来得及
        controller.recordSpeed(ImageUpstream.PIXIV, relay = false, bytes = 1_048_576, elapsedMs = 1_000)

        assertTrue(controller.worthFullImageInline(ImageUpstream.PIXIV))
    }

    /** 判的是窗口内的整体速率，不是"最快那一次"：慢的那笔不能被后来的快取图抹掉 */
    @Test
    fun inlineFullImageJudgesTheWindowNotTheLastFetch() {
        controller.recordSpeed(ImageUpstream.PIXIV, relay = false, bytes = 1_048_576, elapsedMs = 8_000)
        controller.recordSpeed(ImageUpstream.PIXIV, relay = false, bytes = 1_048_576, elapsedMs = 1_000)

        assertFalse(controller.worthFullImageInline(ImageUpstream.PIXIV))
    }

    @Test
    fun inlineFullImageLooksAtTheRouteItWouldActuallyUse() {
        // 直连 26KB/s、中继 1MB/s：自动选择会走中继，预算就该按中继算
        controller.recordSpeed(ImageUpstream.PIXIV, relay = false, bytes = 1_048_576, elapsedMs = 40_000)
        controller.recordSpeed(ImageUpstream.PIXIV, relay = true, bytes = 1_048_576, elapsedMs = 1_000)

        assertTrue(controller.useRelay(ImageUpstream.PIXIV))
        assertTrue(controller.worthFullImageInline(ImageUpstream.PIXIV))
    }
}
