package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class AddressHealthTest {

    private var now = 0L
    private val health = AddressHealth(now = { now }, jitter = { 0L })

    private val ipA = InetAddress.getByName("1.1.1.1")
    private val ipB = InetAddress.getByName("2.2.2.2")

    private fun fail(type: DoHDns.FailureType, address: InetAddress = ipA): Long =
        health.onFailure(HOST, address, type) - now

    @Test
    fun connectFailureRetriesInSecondsNotMinutes() {
        // 旧实现给连接失败 2 分钟 TTL：一次瞬时抖动就换来整段时间不可用
        assertEquals(AddressHealth.CONNECT_BASE_MS, fail(DoHDns.FailureType.CONNECT))
    }

    @Test
    fun tlsFailureStartsHigherThanConnectFailure() {
        assertEquals(AddressHealth.TLS_BASE_MS, fail(DoHDns.FailureType.TLS))
        assertTrue(AddressHealth.TLS_BASE_MS > AddressHealth.CONNECT_BASE_MS)
    }

    @Test
    fun backoffGrowsAndCapsAtOneMinute() {
        val gaps = (1..8).map { fail(DoHDns.FailureType.CONNECT) }

        assertEquals(
            listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L, 60_000L),
            gaps,
        )
    }

    @Test
    fun probationBlocksSelectionUntilItExpires() {
        fail(DoHDns.FailureType.CONNECT)

        assertTrue(health.isOnProbation(HOST, ipA))
        now += AddressHealth.CONNECT_BASE_MS
        assertFalse(health.isOnProbation(HOST, ipA))
    }

    @Test
    fun expiredProbationKeepsFailureCountSoNextBackoffGrows() {
        fail(DoHDns.FailureType.CONNECT)
        now += AddressHealth.CONNECT_BASE_MS

        // 到期只表示"可以再试"，计数要留到成功为止，否则反复失败的地址每轮都从基点重来
        assertEquals(1, health.failures(HOST, ipA))
        assertEquals(4_000L, fail(DoHDns.FailureType.CONNECT))
    }

    @Test
    fun successClearsProbationAndFailureCount() {
        fail(DoHDns.FailureType.TLS)
        now += AddressHealth.TLS_BASE_MS - 1
        assertTrue(health.isOnProbation(HOST, ipA))

        health.onSuccess(HOST, ipA)

        assertFalse(health.isOnProbation(HOST, ipA))
        assertEquals(0, health.failures(HOST, ipA))
        assertEquals(AddressHealth.CONNECT_BASE_MS, fail(DoHDns.FailureType.CONNECT))
    }

    @Test
    fun recordsArePerAddress() {
        fail(DoHDns.FailureType.CONNECT, ipA)

        assertTrue(health.isOnProbation(HOST, ipA))
        assertFalse(health.isOnProbation(HOST, ipB))
    }

    @Test
    fun recordsArePerHostname() {
        fail(DoHDns.FailureType.CONNECT, ipA)

        assertFalse(health.isOnProbation("cdn.poipiku.com", ipA))
    }

    @Test
    fun jitterIsAddedOnTopOfBackoff() {
        val jittered = AddressHealth(now = { now }, jitter = { 500L })

        assertEquals(
            AddressHealth.CONNECT_BASE_MS + 500L,
            jittered.onFailure(HOST, ipA, DoHDns.FailureType.CONNECT) - now,
        )
    }

    private companion object {
        const val HOST = "poipiku.com"
    }
}
