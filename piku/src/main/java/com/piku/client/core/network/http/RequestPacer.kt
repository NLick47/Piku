package com.piku.client.core.network.http

import com.piku.client.core.model.WorkSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RequestPacer @Inject constructor() {

    private val locks = ConcurrentHashMap<WorkSource, Mutex>()
    private val lastAt = ConcurrentHashMap<WorkSource, Long>()

    suspend fun await(source: WorkSource, gapMs: Long = DEFAULT_GAP_MS) {
        locks.getOrPut(source) { Mutex() }.withLock {
            val now = System.currentTimeMillis()
            val wait = gapMs - (now - (lastAt[source] ?: 0L))
            if (wait > 0) delay(wait)
            lastAt[source] = System.currentTimeMillis()
        }
    }

    companion object {
        const val DEFAULT_GAP_MS = 500L
    }
}
