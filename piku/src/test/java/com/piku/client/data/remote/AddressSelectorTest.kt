package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

class AddressSelectorTest {

    private var now = 0L
    private val health = AddressHealth(now = { now }, jitter = { 0L })
    private val selector = AddressSelector(health, { now })

    private val ipA = InetAddress.getByName("1.1.1.1")
    private val ipB = InetAddress.getByName("2.2.2.2")
    private val ipC = InetAddress.getByName("3.3.3.3")

    private fun order(pool: List<InetAddress>, cap: Int = 2, includeWaiting: Boolean = false) =
        selector.order(HOST, pool, cap, includeWaiting)

    @Test
    fun healthyAddressesKeepPreferenceOrderUpToCap() {
        assertEquals(listOf(ipA, ipB), order(listOf(ipA, ipB, ipC), cap = 2))
    }

    @Test
    fun duplicatesCollapse() {
        assertEquals(listOf(ipA, ipB), order(listOf(ipA, ipB, ipA), cap = 2))
    }

    @Test
    fun addressOnProbationIsSkipped() {
        health.onFailure(HOST, ipA, DoHDns.FailureType.CONNECT)

        // 备选顶上来，而不是把刚失败的地址再交一次
        assertEquals(listOf(ipB), order(listOf(ipA, ipB)))
    }

    @Test
    fun freshAddressesOutrankRecoveredOnes() {
        health.onFailure(HOST, ipA, DoHDns.FailureType.CONNECT)
        now += AddressHealth.CONNECT_BASE_MS

        // 池顺序把 ipA 放在前面，但它刚出缓刑期：先让从没失败过的 ipB 顶上
        assertEquals(listOf(ipB, ipA), order(listOf(ipA, ipB), cap = 2))
    }

    @Test
    fun recoveredAddressesAreOrderedByWhoUnjamsFirst() {
        // ipA 先失败（TLS，5s），ipB 后失败（连接，2s）：解禁顺序与池顺序相反
        health.onFailure(HOST, ipA, DoHDns.FailureType.TLS)
        now += 100
        health.onFailure(HOST, ipB, DoHDns.FailureType.CONNECT)
        now = AddressHealth.TLS_BASE_MS

        assertEquals(listOf(ipB, ipA), order(listOf(ipA, ipB), cap = 2))
    }

    @Test
    fun waitingAddressIsNotUsedUnlessExplicitlyRequested() {
        health.onFailure(HOST, ipA, DoHDns.FailureType.CONNECT)

        assertEquals(emptyList<InetAddress>(), order(listOf(ipA)))
        assertEquals(listOf(ipA), order(listOf(ipA), includeWaiting = true))
    }

    @Test
    fun lastResortPicksTheOneThatUnjamsFirst() {
        health.onFailure(HOST, ipB, DoHDns.FailureType.TLS) // 5s
        health.onFailure(HOST, ipA, DoHDns.FailureType.CONNECT) // 2s

        assertEquals(listOf(ipA), order(listOf(ipB, ipA), cap = 1, includeWaiting = true))
    }

    @Test
    fun fillIsBoundedByCap() {
        health.onFailure(HOST, ipB, DoHDns.FailureType.CONNECT)
        health.onFailure(HOST, ipC, DoHDns.FailureType.CONNECT)
        now += AddressHealth.CONNECT_BASE_MS

        assertEquals(listOf(ipA, ipB), order(listOf(ipA, ipB, ipC), cap = 2))
    }

    @Test
    fun zeroCapYieldsNothing() {
        assertEquals(emptyList<InetAddress>(), order(listOf(ipA, ipB), cap = 0))
    }

    private companion object {
        const val HOST = "poipiku.com"
    }
}
