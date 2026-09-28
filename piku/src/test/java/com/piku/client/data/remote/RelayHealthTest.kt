package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class RelayHealthTest {

    private var now = 0L
    private val health = RelayHealth(listOf("relayA", "relayB"), now = { now })

    @Test
    fun candidatesKeepOrder() {
        assertEquals(listOf("relayA", "relayB"), health.candidates())
    }

    @Test
    fun failureDoesNotRemoveHostFromCandidates() {
        health.onFailure("relayA")

        // 直连不可用时中转是唯一出路：失败一次不能把它挡在候选外
        assertEquals(listOf("relayA", "relayB"), health.candidates())

        now += 60_000
        assertEquals(listOf("relayA", "relayB"), health.candidates())
    }

    @Test
    fun successMovesHostToFront() {
        health.onSuccess("relayB")

        assertEquals(listOf("relayB", "relayA"), health.candidates())
    }

    @Test
    fun statesReportFailuresAndLastSuccess() {
        health.onFailure("relayA")
        health.onSuccess("relayB")

        val states = health.states()

        assertEquals(listOf("relayB", "relayA"), states.map { it.host })
        assertEquals(0, states[0].failures)
        assertEquals(now, states[0].lastSuccessAt)
        assertEquals(1, states[1].failures)
        assertEquals(null, states[1].lastSuccessAt)
    }
}
