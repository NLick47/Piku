package com.piku.client.data.remote

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

internal class RelayHealth(
    hosts: List<String>,
    private val now: () -> Long,
) {

    data class HostState(
        val host: String,
        /** 累计失败次数（成功不清零，用于诊断"这条线路偶发"） */
        val failures: Int,
        val lastSuccessAt: Long?,
    )

    private val order = AtomicReference(hosts)
    private val failures = ConcurrentHashMap<String, Int>()
    private val lastSuccessAt = ConcurrentHashMap<String, Long>()

    /** 候选线路，按当前优先级排列 */
    fun candidates(): List<String> = order.get()

    fun onSuccess(host: String) {
        lastSuccessAt[host] = now()
        order.updateAndGet { current -> listOf(host) + current.filter { it != host } }
    }

    fun onFailure(host: String) {
        failures.merge(host, 1, Int::plus)
    }

    /** 诊断用：按当前优先级列出每条线路的状态 */
    fun states(): List<HostState> = order.get().map { host ->
        HostState(host = host, failures = failures[host] ?: 0, lastSuccessAt = lastSuccessAt[host])
    }
}
