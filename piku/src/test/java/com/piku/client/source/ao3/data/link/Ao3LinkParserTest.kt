package com.piku.client.source.ao3.data.link

import com.piku.client.core.model.WorkKind
import com.piku.client.core.model.WorkSource
import com.piku.client.core.source.SourceLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ao3LinkParserTest {

    private val parser = Ao3LinkParser()

    @Test
    fun parsesWorkUrl() {
        val link = parser.parse("https://archiveofourown.org/works/20251606")
        assertEquals(SourceLink.Work(WorkSource.AO3, 20251606L, WorkKind.NOVEL), link)
    }

    @Test
    fun toleratesSchemeAndWwwAndTrailingSlash() {
        listOf(
            "archiveofourown.org/works/20251606",
            "www.archiveofourown.org/works/20251606",
            "https://www.archiveofourown.org/works/20251606/",
            "http://archiveofourown.org/works/20251606",
        ).forEach { url ->
            assertNull("不应漏解析: $url", parser.parse(url).let { if (it is SourceLink.Work) null else it })
            assertEquals("解析值应一致: $url", 20251606L, (parser.parse(url) as SourceLink.Work).workId)
        }
    }

    @Test
    fun toleratesQueryAndFullWorkFlag() {
        val link = parser.parse("https://archiveofourown.org/works/20251606?view_full_work=true")
        assertEquals(20251606L, (link as SourceLink.Work).workId)
    }

    @Test
    fun chapterUrlMapsToSameWork() {
        val link = parser.parse("https://archiveofourown.org/works/20251606/chapters/47998306")
        assertEquals(20251606L, (link as SourceLink.Work).workId)
    }

    @Test
    fun rejectsNonWorkPaths() {
        listOf(
            "https://archiveofourown.org/",
            "https://archiveofourown.org/users/thishasbeencary",
            "https://archiveofourown.org/tags/Yuri!!!%20on%20Ice%20(Anime)/works",
            "https://archiveofourown.org/works/search?work_search[query]=x",
            "https://evil.com/works/20251606",
        ).forEach { url ->
            assertNull("应拒判: $url", parser.parse(url))
        }
    }

    @Test
    fun rejectsOverlongAndGarbage() {
        assertNull(parser.parse(""))
        assertNull(parser.parse("   "))
        assertNull(parser.parse("https://archiveofourown.org/works/notanumber"))
        assertNull(parser.parse("https://archiveofourown.org/" + "a".repeat(600) + "/works/1"))
    }

    @Test
    fun declaresOwnHosts() {
        assertEquals(setOf("archiveofourown.org", "www.archiveofourown.org"), parser.hosts)
        assertEquals(WorkSource.AO3, parser.sourceId)
    }
}
