package com.piku.client.data.remote

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

internal class AddressHealth(
    private val now: () -> Long,
    private val maxBackoffMs: Long = MAX_BACKOFF_MS,
    private val jitter: (Long) -> Long = RANDOM_JITTER,
) {

    private class Record(
        val hostname: String,
        val address: InetAddress,
        var failures: Int,
        var retryAt: Long,
    )

    private val records = ConcurrentHashMap<String, Record>()

    /** 记一次失败，返回该地址的下次可试时间 */
    fun onFailure(hostname: String, address: InetAddress, type: DoHDns.FailureType): Long =
        records.compute(key(hostname, address)) { _, existing ->
            val failures = (existing?.failures ?: 0) + 1
            Record(hostname, address, failures, now() + backoffMillis(type, failures))
        }!!.retryAt

    fun onSuccess(hostname: String, address: InetAddress) {
        records.remove(key(hostname, address))
    }

    /** 缓刑期内不可选 */
    fun isOnProbation(hostname: String, address: InetAddress): Boolean =
        retryAt(hostname, address) > now()

    /** 下次可试时间；不在缓刑期返回 [Long.MIN_VALUE]，排序时自然排最前 */
    fun retryAt(hostname: String, address: InetAddress): Long =
        records[key(hostname, address)]?.retryAt ?: Long.MIN_VALUE

    fun failures(hostname: String, address: InetAddress): Int =
        records[key(hostname, address)]?.failures ?: 0

    /** 诊断用：该域名下所有失败过的地址，按解禁先后排 */
    fun snapshot(hostname: String): List<Snapshot> =
        records.values
            .filter { it.hostname == hostname }
            .map { Snapshot(it.address, it.failures, it.retryAt) }
            .sortedBy { it.retryAt }

    data class Snapshot(val address: InetAddress, val failures: Int, val retryAt: Long)

    private fun backoffMillis(type: DoHDns.FailureType, failures: Int): Long {
        val base = when (type) {
            DoHDns.FailureType.TLS -> TLS_BASE_MS
            DoHDns.FailureType.CONNECT -> CONNECT_BASE_MS
            DoHDns.FailureType.STREAM -> STREAM_BASE_MS
        }
        val factor = 1L shl (failures - 1).coerceIn(0, MAX_SHIFT)
        val capped = (base * factor).coerceAtMost(maxBackoffMs)
        return capped + jitter(capped)
    }

    private fun key(hostname: String, address: InetAddress) = "$hostname|${address.hostAddress}"

    companion object {
        /** 缓刑上限：持续故障的地址最多每分钟复检一次 */
        const val MAX_BACKOFF_MS = 60_000L

        /** TLS 失败更可疑（对端可能已不是目标站点），起点比连接失败高 */
        const val TLS_BASE_MS = 5_000L
        const val CONNECT_BASE_MS = 2_000L
        const val STREAM_BASE_MS = 1_000L

        private const val MAX_SHIFT = 6

        /** 抖动：避免同一网络故障下所有地址同时解禁、又同时被探测 */
        val RANDOM_JITTER: (Long) -> Long = { base ->
            ThreadLocalRandom.current().nextLong(0L, base / 4 + 1L)
        }
    }
}
