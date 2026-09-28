package com.piku.client.data.remote

import java.net.InetAddress

internal class AddressSelector(
    private val health: AddressHealth,
    private val now: () -> Long,
) {

    fun order(
        hostname: String,
        pool: List<InetAddress>,
        cap: Int,
        includeWaiting: Boolean = false,
    ): List<InetAddress> {
        if (cap <= 0) return emptyList()
        val distinct = pool.distinct()
        val selectable = distinct.filterNot { health.isOnProbation(hostname, it) }
        val fresh = selectable.filter { health.failures(hostname, it) == 0 }
        val recovered = selectable
            .filter { health.failures(hostname, it) > 0 }
            .sortedBy { health.retryAt(hostname, it) }
        val ordered = fresh + recovered
        if (ordered.isNotEmpty()) return ordered.take(cap)
        if (!includeWaiting) return emptyList()
        return distinct
            .filter { health.isOnProbation(hostname, it) }
            .sortedBy { health.retryAt(hostname, it) }
            .take(cap)
    }
}
