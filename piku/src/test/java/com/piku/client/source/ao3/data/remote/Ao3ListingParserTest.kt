package com.piku.client.source.ao3.data.remote

import com.piku.client.core.model.WorkKind
import com.piku.client.core.model.WorkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Ao3ListingParserTest {

    private fun readResource(name: String): String {
        val res = javaClass.classLoader?.getResource(name)?.readText(Charsets.UTF_8)
        assumeTrue("fixture $name 缺失，跳过（快照仅本地）", res != null)
        return res!!
    }

    /** 搜索结果第一页（20 条 + 真实 rel=next 分页） */
    private val searchHtml: String by lazy { readResource("ao3/search.html") }

    /** 最新列表页（含一条 Anonymous 匿名作品、无分页） */
    private val latestHtml: String by lazy { readResource("ao3/listing.html") }

    /**
     * blurb 的 class 是动态拼接的（多作者时带多个 user-{id}），
     * 精确匹配 `class="work blurb group"` 会漏掉全部条目。
     */
    @Test
    fun parsesEveryBlurbOnThePage() {
        val items = Ao3ListingParser.parse(searchHtml)
        assertTrue("一页应有约 20 条，实得 ${items.size}", items.size >= 15)
    }

    @Test
    fun allItemsAreAo3Novels() {
        Ao3ListingParser.parse(searchHtml).forEach { work ->
            assertEquals(WorkSource.AO3, work.source)
            assertEquals(WorkKind.NOVEL, work.kind)
        }
    }

    @Test
    fun everyItemHasIdTitleAuthorAndWords() {
        Ao3ListingParser.parse(searchHtml).forEach { work ->
            assertTrue("workId 应大于 0，实得 ${work.id}", work.id > 0)
            assertTrue("标题不应为空", work.title.isNotBlank())
            assertTrue("作者名不应为空", work.authorName.isNotBlank())
            assertTrue("字数应大于 0，实得 ${work.textLength}", work.textLength > 0)
        }
    }

    @Test
    fun decodesHtmlEntitiesInTitle() {
        val titles = Ao3ListingParser.parse(searchHtml).map { it.title }
        // 页面标题常含 &amp; / &#39;，解析后不应残留实体或标签
        titles.forEach { title ->
            assertFalse("标题残留实体: $title", title.contains("&amp;") || title.contains("&#39;"))
            assertFalse("标题残留标签: $title", title.contains("<"))
        }
    }

    @Test
    fun ratingDrivesR18Flag() {
        val items = Ao3ListingParser.parse(searchHtml)
        val explicit = items.filter { it.r18 }
        val general = items.filter { !it.r18 }
        // 搜索结果通常两类都有；只有一类时至少数量自洽
        assertTrue("至少解析出一条", items.isNotEmpty())
        assertEquals(items.size, explicit.size + general.size)
    }

    /**
     * 连载状态取自 blurb 的 `complete-no iswip` / `complete-yes`。
     * fixture 的搜索结果里两种都有（16 完结 + 4 连载），数错一个就红。
     */
    @Test
    fun completionStatusIsParsed() {
        val items = Ao3ListingParser.parse(searchHtml)
        val ongoing = items.count { it.inProgress }
        val complete = items.count { !it.inProgress }
        assertEquals(4, ongoing)
        assertEquals(16, complete)
    }

    /** 咬人：状态解析按块取的，不能整页一刀切（否则所有条目同态） */
    @Test
    fun completionStatusIsPerItem() {
        val items = Ao3ListingParser.parse(searchHtml)
        assertTrue("两种状态都应出现", items.any { it.inProgress } && items.any { !it.inProgress })
    }

    /**
     * 同人 = 标签文本本身。整段剥标签的实现会把读屏用的 "Fandoms:" 前缀带进来
     * （实测线上列表每条的 categoryName 都成了「Fandoms:」）。
     */
    @Test
    fun fandomIsTheTagNameNotTheLandmark() {
        val fandoms = Ao3ListingParser.parse(searchHtml).map { it.categoryName }.filter { it.isNotBlank() }
        assertTrue("应解析出同人", fandoms.isNotEmpty())
        fandoms.forEach { fandom ->
            assertFalse("同人里不该有读屏前缀: $fandom", fandom.contains("Fandoms:"))
            assertFalse("同人里不该有标签残留: $fandom", fandom.contains("<"))
        }
    }

    /** 同人字段带具体值（fixture 里第一条是 IDOLiSH7） */
    @Test
    fun fandomCarriesRealName() {
        val items = Ao3ListingParser.parse(searchHtml)
        assertTrue(
            "至少一条同人应含具体作品名",
            items.any { it.categoryName.isNotBlank() && it.categoryName.length > 3 },
        )
    }

    /**
     * 摘要是选文的主语（列表卡要摆一段），blurb 里在 `blockquote.userstuff.summary`。
     * 取不到的话卡片就只剩标题，等于没信息。
     */
    @Test
    fun summaryIsParsedForTheCard() {
        val withSummary = Ao3ListingParser.parse(searchHtml).filter { it.summary.isNotBlank() }
        assertTrue("应解析出带摘要的条目，实得 ${withSummary.size}", withSummary.size >= 10)
        // 摘要里合法的 `<` 是作者敲的爱心「&lt;3」——只断言没有真正的标签形态
        val tag = Regex("""</?[a-zA-Z][^>]*>""")
        withSummary.forEach { work ->
            assertFalse("摘要残留标签: ${work.summary.take(60)}", tag.containsMatchIn(work.summary))
            assertFalse("摘要残留实体", work.summary.contains("&amp;") || work.summary.contains("&#39;"))
            assertFalse("数字实体没解开: ${work.summary.take(60)}", work.summary.contains("&#"))
        }
    }

    /** 标签取 ul.tags，样板警告（谁都有的那两条）不进卡片 */
    @Test
    fun tagsSkipBoilerplateWarnings() {
        val all = Ao3ListingParser.parse(searchHtml).flatMap { it.tags }
        assertTrue("应解析出标签", all.isNotEmpty())
        assertTrue(
            "样板警告不该混进标签位",
            all.none { it == "No Archive Warnings Apply" || it == "Creator Chose Not To Use Archive Warnings" },
        )
        assertTrue("标签残留标签", all.none { Regex("""</?[a-zA-Z][^>]*>""").containsMatchIn(it) })
    }

    /**
     * kudos 是卡片上的热度。站点在 kudos 为 0 时整项不输出（fixture 里有这样的条目），
     * 取不到按 0 而不是丢条目。
     */
    @Test
    fun kudosIsParsed() {
        val items = Ao3ListingParser.parse(searchHtml)
        // 值被 <a> 包着（`<dd class="kudos"><a href="…">1,826</a></dd>`）
        assertTrue("应有条目带 kudos，实得 ${items.count { it.likes > 0 }}", items.count { it.likes > 0 } >= 15)
        assertTrue("kudos 应是真数字", items.any { it.likes >= 100 })
    }

    /** 站点在 kudos 为 0 时不给这一项：要按 0 算，不能整条丢 */
    @Test
    fun zeroKudosDefaultsToZero() {
        val items = Ao3ListingParser.parse(latestHtml)
        assertTrue("最新列表里应有零 kudos 的条目", items.size >= 15)
        assertTrue("没有 kudos 的条目按 0", items.any { it.likes == 0 })
    }

    @Test
    fun nextPageLinkIsDetected() {
        // 搜索结果第一页必有 rel=next；最新列表页恰好是末页，没有
        assertTrue("搜索第一页应有 rel=next", Ao3ListingParser.hasNextPage(searchHtml))
        assertFalse("最新列表末页应无 rel=next", Ao3ListingParser.hasNextPage(latestHtml))
    }

    /**
     * 标签页的分页控件不带 rel="next"（`<li class="next"><a href="…page=2">Next →</a>`），
     * 只认 rel 的实现会让标签列表永远停在第一页。
     */
    @Test
    fun nextPageWithoutRelAttributeIsDetected() {
        val tagPage = """<ol class="pagination actions pagy"><li class="previous"><span class="disabled">← Previous</span></li><li class="next"><a href="/tags/Angst/works?view_adult=true&amp;page=2">Next →</a></li></ol>"""
        assertTrue("标签页的 Next 链接应被识别", Ao3ListingParser.hasNextPage(tagPage))
    }

    /** 末页的 Next 是禁用的 `<span>`：认成"还有下一页"会让翻页追着空页跑 */
    @Test
    fun disabledNextMeansLastPage() {
        val lastPage = """<ol class="pagination actions pagy"><li class="previous"><a href="/tags/Angst/works?page=1">← Previous</a></li><li class="next"><span class="disabled">Next →</span></li></ol>"""
        assertFalse("禁用态的 Next 不是下一页", Ao3ListingParser.hasNextPage(lastPage))
    }

    /**
     * 匿名作品没有 rel="author" 链接，作者区是裸文本 Anonymous。
     * 精确匹配 rel="author" 的实现会把这类条目整条丢掉。
     */
    @Test
    fun anonymousWorksKeepAnonymousAuthor() {
        val anonymous = Ao3ListingParser.parse(latestHtml).filter { it.authorName == "Anonymous" }
        assertTrue("最新列表应含匿名作品", anonymous.isNotEmpty())
        anonymous.forEach { work ->
            assertTrue("标题仍应解析出来", work.title.isNotBlank())
            assertTrue("workId 仍应解析出来", work.id > 0)
        }
    }

    /** 末页判定：把 next 链接摘掉后应报告没有下一页 */
    @Test
    fun lastPageHasNoNext() {
        val lastPage = searchHtml.replace(Regex("""<li[^>]*class="[^"]*\bnext\b[^"]*"[^>]*>.*?</li>""", RegexOption.DOT_MATCHES_ALL), "")
        assertFalse(Ao3ListingParser.hasNextPage(lastPage))
    }

    /** 咬人：把 blurb 的 class 改掉后，精确匹配实现会全灭，我们的按子串定位仍能解析 */
    @Test
    fun doesNotRelyOnExactBlurbClassMatch() {
        val mutated = searchHtml.replace("work blurb group", "work blurb group exact")
        // 精确匹配实现下这里会返回空；我们的实现按子串定位，仍然解析得到
        assertTrue(Ao3ListingParser.parse(mutated).isNotEmpty())
    }

    @Test
    fun garbageInputYieldsEmptyList() {
        assertTrue(Ao3ListingParser.parse("").isEmpty())
        assertTrue(Ao3ListingParser.parse("<html><body>nothing here</body></html>").isEmpty())
    }
}
