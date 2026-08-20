package com.piku.client.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoipikuLinkParserTest {

    @Test
    fun `parses work link with html suffix`() {
        val link = parsePoipikuLink("https://poipiku.com/12345/67890.html")
        assertEquals(PoipikuLink.Work(authorId = 12345, workId = 67890), link)
    }

    @Test
    fun `parses work link without html suffix and with trailing slash`() {
        val link = parsePoipikuLink("https://poipiku.com/12345/67890/")
        assertEquals(PoipikuLink.Work(authorId = 12345, workId = 67890), link)
    }

    @Test
    fun `parses work link with www prefix and query params`() {
        val link = parsePoipikuLink("http://www.poipiku.com/12/34.html?p=2#top")
        assertEquals(PoipikuLink.Work(authorId = 12, workId = 34), link)
    }

    @Test
    fun `parses user link with trailing slash`() {
        val link = parsePoipikuLink("https://poipiku.com/98765/")
        assertEquals(PoipikuLink.User(userId = 98765), link)
    }

    @Test
    fun `parses user link with html suffix`() {
        val link = parsePoipikuLink("https://poipiku.com/98765.html")
        assertEquals(PoipikuLink.User(userId = 98765), link)
    }

    @Test
    fun `parses user link without scheme`() {
        val link = parsePoipikuLink("poipiku.com/123")
        assertEquals(PoipikuLink.User(userId = 123), link)
    }

    @Test
    fun `parses user link with m subdomain`() {
        val link = parsePoipikuLink("https://m.poipiku.com/123/")
        assertEquals(PoipikuLink.User(userId = 123), link)
    }

    @Test
    fun `ignores surrounding whitespace and trailing query`() {
        val link = parsePoipikuLink("  https://poipiku.com/1/2.html?x=y  ")
        assertEquals(PoipikuLink.Work(authorId = 1, workId = 2), link)
    }

    @Test
    fun `returns null for non poipiku domain`() {
        assertNull(parsePoipikuLink("https://example.com/123/456.html"))
        assertNull(parsePoipikuLink("https://poipiku.com.evil.com/123/"))
    }

    @Test
    fun `returns null for poipiku-like but not a link`() {
        assertNull(parsePoipikuLink("poipiku"))
        assertNull(parsePoipikuLink("poipiku.com"))
        assertNull(parsePoipikuLink("https://poipiku.com/"))
    }

    @Test
    fun `returns null for non numeric path`() {
        assertNull(parsePoipikuLink("https://poipiku.com/abc/def.html"))
        assertNull(parsePoipikuLink("https://poipiku.com/123/abc.html"))
    }

    @Test
    fun `returns null for plain keywords`() {
        assertNull(parsePoipikuLink("東方プロジェクト"))
        assertNull(parsePoipikuLink(""))
        assertNull(parsePoipikuLink("#tag"))
    }

    @Test
    fun `returns null for oversized input`() {
        val long = "https://poipiku.com/123/456.html" + "a".repeat(600)
        assertNull(parsePoipikuLink(long))
    }

    @Test
    fun `work ids survive long values`() {
        val link = parsePoipikuLink("https://poipiku.com/9000000001/9000000002.html")
        assertTrue(link is PoipikuLink.Work)
        link as PoipikuLink.Work
        assertEquals(9000000001L, link.authorId)
        assertEquals(9000000002L, link.workId)
    }
}