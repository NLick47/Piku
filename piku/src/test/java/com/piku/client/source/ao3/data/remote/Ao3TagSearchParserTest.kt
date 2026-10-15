package com.piku.client.source.ao3.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 标签搜索结果解析。fixture 是 2026-10-14 实抓的页面
 * （`tag_search[name]=harry potter&type=Fandom&wrangling_status=canonical&sort=uses desc`）。
 */
class Ao3TagSearchParserTest {

    private val tagsHtml: String by lazy {
        val res = javaClass.classLoader?.getResource("ao3/tags.html")?.readText(Charsets.UTF_8)
        assumeTrue("fixture ao3/tags.html 缺失，跳过（快照仅本地）", res != null)
        res!!
    }

    @Test
    fun parsesEveryResultRowOnThePage() {
        assertEquals(8, Ao3TagSearchParser.parse(tagsHtml).size)
    }

    /** 圈层值必须是标签名本体：href 里 `*d*`/`*s*` 是转义，拿 href 当名字收不窄任何东西 */
    @Test
    fun namesComeFromAnchorTextNotHref() {
        val names = Ao3TagSearchParser.parse(tagsHtml).map { it.name }
        assertEquals("Harry Potter - J. K. Rowling", names.first())
        names.forEach { name ->
            assertFalse("名字里混进了 href 转义: $name", name.contains("*d*") || name.contains("*s*"))
            assertFalse("名字里混进了路径: $name", name.contains("/tags/"))
        }
    }

    /** 标签名里的 `&amp;` 要解成 `&`；`&lrm;`（读屏方向标记）不能落进名字 */
    @Test
    fun decodesEntitiesInNames() {
        val names = Ao3TagSearchParser.parse(tagsHtml).map { it.name }
        assertTrue(
            "带 & 的名字没解实体: $names",
            names.contains("Harry Potter and the Cursed Child - Thorne & Rowling"),
        )
        names.forEach { name ->
            assertFalse("名字残留实体: $name", name.contains("&amp;") || name.contains("&#") || name.contains("&lrm;"))
        }
    }

    /** 作品数在链接之后：`…</a> &lrm;(1025170)` */
    @Test
    fun parsesWorkCounts() {
        val items = Ao3TagSearchParser.parse(tagsHtml)
        assertEquals(1025170, items.first().count)
        assertTrue("小数字也要取到: $items", items.any { it.count == 7 })
    }

    /** 站点按使用量降序给的，解析要保持原序（联想列表里最热门的那条得在最前） */
    @Test
    fun keepsSiteOrderByUses() {
        val items = Ao3TagSearchParser.parse(tagsHtml)
        val counts = items.map { it.count ?: 0 }
        assertEquals("顺序被打乱: $counts", counts.sortedDescending(), counts)
    }

    @Test
    fun emptyResultListParsesToEmpty() {
        assertTrue(Ao3TagSearchParser.parse("""<ol class="tag index group">\n  </ol>""").isEmpty())
    }

    /** 非结果页（403 挑战页、错误页、空 body）不能崩，也不能编出条目 */
    @Test
    fun toleratesNonResultBodies() {
        assertTrue(Ao3TagSearchParser.parse("").isEmpty())
        assertTrue(Ao3TagSearchParser.parse("<html><body>Just a moment...</body></html>").isEmpty())
        assertTrue(Ao3TagSearchParser.parse("""[{"id":"x"}]""").isEmpty())
    }
}
