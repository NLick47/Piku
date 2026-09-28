package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

class CachingAddressSourceTest {

    private var now = 0L
    private var usable = true
    private var calls = 0

    private val ipA = InetAddress.getByName("1.1.1.1")

    private fun source(answer: () -> List<InetAddress>) = CachingAddressSource(
        ttlMs = 30_000,
        clock = { now },
        isUsable = { _, _ -> usable },
        delegate = AddressSource {
            calls++
            answer()
        },
    )

    @Test
    fun cachedAnswerIsReusedWithinTtl() {
        val source = source { listOf(ipA) }

        source.resolve(HOST)
        source.resolve(HOST)

        assertEquals(1, calls)
    }

    @Test
    fun expiredAnswerIsQueriedAgain() {
        val source = source { listOf(ipA) }

        source.resolve(HOST)
        now += 30_000
        source.resolve(HOST)

        assertEquals(2, calls)
    }

    @Test
    fun cacheIsIgnoredWhenEveryCachedAddressIsUnusable() {
        val source = source { listOf(ipA) }
        source.resolve(HOST)

        // 全被降级时继续吃缓存，等于这个源在 TTL 内不会再给出新答案
        usable = false
        source.resolve(HOST)

        assertEquals(2, calls)
    }

    @Test
    fun emptyAnswerIsNotCached() {
        val source = source { emptyList() }

        source.resolve(HOST)
        source.resolve(HOST)

        assertEquals(2, calls)
    }

    @Test
    fun cachedExposesUnexpiredAnswerOnly() {
        val source = source { listOf(ipA) }
        source.resolve(HOST)

        assertEquals(listOf(ipA), source.cached(HOST))

        now += 30_000

        assertEquals(emptyList<InetAddress>(), source.cached(HOST))
    }

    private companion object {
        const val HOST = "poipiku.com"
    }
}
