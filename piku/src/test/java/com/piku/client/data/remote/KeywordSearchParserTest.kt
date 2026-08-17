package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class KeywordSearchParserTest {


    private fun readResource(name: String): String {
        val res = javaClass.classLoader?.getResource(name)?.readText()
        assumeTrue("fixture $name 缺失，跳过（快照仅本地）", res != null)
        return res!!
    }

    @Test
    fun `parses keyword search page with same parser as feed`() {
        val works = NewArrivalParser.parse(readResource("keywordsearch.html"))
        // 东方关键词搜索首页实测 48 条 IllustThumb，结构同 NewArrivalPcV
        assertEquals(48, works.size)
        val first = works.first()
        assertTrue(first.authorName.isNotBlank())
        assertTrue(first.thumbnailUrl.contains("cdn.poipiku.com"))
        assertTrue(first.title.isNotBlank())
        assertTrue(works.all { it.id > 0 && it.authorId > 0 })
        assertTrue(works.any { it.warning })
    }

    @Test
    fun `parses empty keyword search page as empty list`() {
        val works = NewArrivalParser.parse("""
            <html><body><div class="IllustListEmpty">該当する作品が見つかりません</div></body></html>
        """.trimIndent())
        assertEquals(0, works.size)
    }
}
