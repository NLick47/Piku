package com.piku.client.domain.source

import com.piku.client.domain.model.WorkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoipikuLinkParserTest {

    @Test
    fun parsesCanonicalWorkUrl() {
        assertEquals(
            SourceLink.Work(WorkSource.POIPIKU, workId = 67890, authorId = 12345),
            parsePoipikuLink("https://poipiku.com/12345/67890.html"),
        )
    }

    @Test
    fun parsesWorkUrlWithoutHtmlSuffixOrWithTrailingSlash() {
        assertEquals(
            SourceLink.Work(WorkSource.POIPIKU, workId = 67890, authorId = 12345),
            parsePoipikuLink("https://poipiku.com/12345/67890/"),
        )
    }

    @Test
    fun parsesWorkUrlWithSubdomainQueryAndAnchor() {
        assertEquals(
            SourceLink.Work(WorkSource.POIPIKU, workId = 34, authorId = 12),
            parsePoipikuLink("http://www.poipiku.com/12/34.html?p=2#top"),
        )
    }

    @Test
    fun parsesAuthorUrlWithTrailingSlash() {
        assertEquals(
            SourceLink.User(WorkSource.POIPIKU, userId = 98765),
            parsePoipikuLink("https://poipiku.com/98765/"),
        )
    }

    @Test
    fun parsesAuthorUrlWithHtmlSuffix() {
        assertEquals(
            SourceLink.User(WorkSource.POIPIKU, userId = 98765),
            parsePoipikuLink("https://poipiku.com/98765.html"),
        )
    }

    @Test
    fun parsesAuthorUrlWithoutScheme() {
        assertEquals(
            SourceLink.User(WorkSource.POIPIKU, userId = 123),
            parsePoipikuLink("poipiku.com/123"),
        )
    }

    @Test
    fun parsesAuthorUrlOnMobileSubdomain() {
        assertEquals(
            SourceLink.User(WorkSource.POIPIKU, userId = 123),
            parsePoipikuLink("https://m.poipiku.com/123/"),
        )
    }

    @Test
    fun toleratesSurroundingWhitespaceAndQueryString() {
        assertEquals(
            SourceLink.Work(WorkSource.POIPIKU, workId = 2, authorId = 1),
            parsePoipikuLink("  https://poipiku.com/1/2.html?x=y  "),
        )
    }

    @Test
    fun preservesIdsBeyondIntRange() {
        // id 以 Long 承接：截成 Int 会静默改写作者/作品指向
        val link = parsePoipikuLink("https://poipiku.com/9000000001/9000000002.html")
        assertTrue(link is SourceLink.Work)
        link as SourceLink.Work
        assertEquals(9000000001L, link.authorId)
        assertEquals(9000000002L, link.workId)
    }

    @Test
    fun rejectsIdsBeyondLongRangeInsteadOfCrashing() {
        // 数字段无上限，超出 Long 必须按「不是链接」返回 null 而不是抛 NumberFormatException：
        // 该解析在搜索框每次键入都会执行，抛异常即崩 App
        assertNull(parsePoipikuLink("https://poipiku.com/99999999999999999999/1.html"))
        assertNull(parsePoipikuLink("https://poipiku.com/1/99999999999999999999.html"))
    }

    @Test
    fun rejectsForeignDomainsAndHostLookalikes() {
        assertNull(parsePoipikuLink("https://example.com/123/456.html"))
        assertNull(parsePoipikuLink("https://poipiku.com.evil.com/123/"))
    }

    @Test
    fun rejectsSitePagesThatAreNotWorksOrAuthors() {
        assertNull(parsePoipikuLink("poipiku"))
        assertNull(parsePoipikuLink("poipiku.com"))
        assertNull(parsePoipikuLink("https://poipiku.com/"))
    }

    @Test
    fun rejectsNonNumericPathSegments() {
        assertNull(parsePoipikuLink("https://poipiku.com/abc/def.html"))
        assertNull(parsePoipikuLink("https://poipiku.com/123/abc.html"))
    }

    @Test
    fun rejectsPlainKeywords() {
        assertNull(parsePoipikuLink("東方プロジェクト"))
        assertNull(parsePoipikuLink(""))
        assertNull(parsePoipikuLink("#tag"))
    }

    @Test
    fun rejectsOversizedInputBeforeMatching() {
        // 防滥用闸门：超长输入不进正则
        val long = "https://poipiku.com/123/456.html" + "a".repeat(600)
        assertNull(parsePoipikuLink(long))
    }
}
