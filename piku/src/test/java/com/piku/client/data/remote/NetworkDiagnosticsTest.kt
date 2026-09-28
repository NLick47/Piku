package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDiagnosticsTest {

    private var now = 1_700_000_000_000L
    private val diagnostics = NetworkDiagnostics(
        NetworkRuntime(now = { now }, sleeper = {}),
    )

    @Test
    fun repeatedIdenticalEventsCollapseIntoOneCountedEntry() {
        diagnostics.warn("degrade host=poipiku.com")
        diagnostics.warn("degrade host=poipiku.com")
        diagnostics.warn("degrade host=poipiku.com")

        val entries = diagnostics.snapshot()

        assertEquals(1, entries.size)
        assertEquals(3, entries.single().repeat)
    }

    @Test
    fun differentMessagesStaySeparateAndNewestComesFirst() {
        diagnostics.info("resolve ok")
        diagnostics.warn("degrade")

        assertEquals(listOf("degrade", "resolve ok"), diagnostics.snapshot().map { it.message })
    }

    @Test
    fun ringBufferKeepsOnlyTheNewestEntries() {
        repeat(NetworkDiagnostics.MAX_ENTRIES + 10) { index -> diagnostics.info("event-$index") }

        val entries = diagnostics.snapshot()

        assertEquals(NetworkDiagnostics.MAX_ENTRIES, entries.size)
        assertEquals("event-${NetworkDiagnostics.MAX_ENTRIES + 9}", entries.first().message)
        assertTrue(entries.none { it.message == "event-0" })
    }

    @Test
    fun entriesCarryTheCurrentTime() {
        diagnostics.info("resolve ok")
        now += 1_500
        diagnostics.warn("degrade")

        assertEquals(
            listOf(now, now - 1_500),
            diagnostics.snapshot().map { it.atMillis },
        )
    }

    @Test
    fun eventLinesMarkWarningsAndCollapseRepeats() {
        diagnostics.recordConnectionProtocol("h2")
        diagnostics.info("resolve ok")
        diagnostics.warn("connect failed host=poipiku.com")
        diagnostics.warn("connect failed host=poipiku.com")

        val lines = diagnostics.eventLines()

        assertEquals("h2", diagnostics.lastProtocol)
        assertEquals(2, lines.size)
        assertTrue(lines.first().startsWith("![") && lines.first().contains("connect failed host=poipiku.com (×2)"))
        assertTrue(lines.last().startsWith(" ["))
    }

    @Test
    fun clearDropsEventsAndProtocol() {
        diagnostics.recordConnectionProtocol("h2")
        diagnostics.info("resolve ok")

        diagnostics.clear()

        assertEquals(emptyList<NetworkDiagnostics.Entry>(), diagnostics.snapshot())
        assertNull(diagnostics.lastProtocol)
    }
}
