package com.piku.client.core.network.image

import com.piku.client.core.network.dns.DoHDns
import com.piku.client.core.network.http.NetworkTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCallBudgetTest {

    @Test
    fun firstAttemptsAlwaysFit() {
        assertTrue(ImageCallBudget.hasRoom(0, ImageCallBudget.directWorstMs, keepNativeReserve = true))
        assertTrue(ImageCallBudget.hasRoom(0, ImageCallBudget.relayWorstMs, keepNativeReserve = true))
    }

    @Test
    fun directBlackholeLeavesNoRoomForRelay() {
        // 直连吃满 10s 后，中继 5s + 原生预留 4.5s 已经超过 15s
        assertFalse(ImageCallBudget.hasRoom(10_000, ImageCallBudget.relayWorstMs, keepNativeReserve = true))
    }

    @Test
    fun relayTimeoutLeavesNoRoomForDirect() {
        assertFalse(ImageCallBudget.hasRoom(5_000, ImageCallBudget.directWorstMs, keepNativeReserve = true))
    }

    @Test
    fun fastFailureStillLeavesRoom() {
        assertTrue(ImageCallBudget.hasRoom(200, ImageCallBudget.relayWorstMs, keepNativeReserve = true))
    }

    @Test
    fun noReserveDoesNotHoldBackNativeBudget() {
        // 没有原生可退时不预留 4.5s，常规线路可以比有原生时多走一轮
        assertFalse(ImageCallBudget.hasRoom(3_000, ImageCallBudget.directWorstMs, keepNativeReserve = true))
        assertTrue(ImageCallBudget.hasRoom(3_000, ImageCallBudget.directWorstMs, keepNativeReserve = false))
    }

    @Test
    fun noReserveStillKeepsTheCallFromTimingOut() {
        assertFalse(ImageCallBudget.hasRoom(15_000, ImageCallBudget.directWorstMs, keepNativeReserve = false))
        assertFalse(ImageCallBudget.hasRoom(10_000, ImageCallBudget.relayWorstMs, keepNativeReserve = false))
        assertTrue(ImageCallBudget.hasRoom(0, ImageCallBudget.directWorstMs, keepNativeReserve = false))
    }

    @Test
    fun nativeBudgetUsesTheWholeCallWhenStartingFresh() {
        assertEquals(
            "地址池要逐个试，别把原生预算卡死在几秒",
            NetworkTuning.IMAGE_CALL_TIMEOUT_MS - 1_000L,
            ImageCallBudget.nativeBudgetMs(0).toLong(),
        )
    }

    @Test
    fun nativeBudgetCoversEveryAddressWithThreeSecondsEach() {
        val addressCount = DoHDns.STATIC_ADDRESSES.getValue(ImageUpstream.POIPIKU.host).size

        assertTrue(
            "整张表每个地址都要有 3s，预算不能把后面的地址挤掉",
            ImageCallBudget.nativeBudgetMs(0) >= addressCount * 3_000,
        )
    }

    @Test
    fun nativeBudgetShrinksWithElapsedTime() {
        assertEquals(ImageCallBudget.maxNativeBudgetMs, ImageCallBudget.nativeBudgetMs(0).toLong())
        assertEquals(17_000, ImageCallBudget.nativeBudgetMs(12_000))
        assertEquals(9_000, ImageCallBudget.nativeBudgetMs(20_000))
        assertEquals("连一次最省的尝试都不够就别开连接", 0, ImageCallBudget.nativeBudgetMs(29_000))
    }

    @Test
    fun boundaryIsInclusive() {
        val used = NetworkTuning.MANAGED_ROUTE_BUDGET_MS -
            ImageCallBudget.nativeReserveMs - ImageCallBudget.relayWorstMs

        assertTrue(ImageCallBudget.hasRoom(used, ImageCallBudget.relayWorstMs, keepNativeReserve = true))
        assertFalse(ImageCallBudget.hasRoom(used + 1, ImageCallBudget.relayWorstMs, keepNativeReserve = true))
    }
}
