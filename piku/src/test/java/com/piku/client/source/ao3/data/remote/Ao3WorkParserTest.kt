package com.piku.client.source.ao3.data.remote

import com.piku.client.core.model.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Ao3WorkParserTest {

    private fun readResource(name: String): String {
        val res = javaClass.classLoader?.getResource(name)?.readText(Charsets.UTF_8)
        assumeTrue("fixture $name 缺失，跳过（快照仅本地）", res != null)
        return res!!
    }

    private val fullHtml: String by lazy { readResource("ao3/work_full.html") }

    private val detail by lazy { Ao3WorkParser.parse(fullHtml, 20251606L) }

    @Test
    fun parsesTitleAndAuthor() {
        assertEquals("Kamasutra", detail.title)
        assertEquals("KatsudonKo", detail.authorName)
        assertEquals("KatsudonKo", detail.authorUser)
    }

    /** 全文页一页含全部章节：37 个章容器，这是"全文一次拉、按章读"策略的前提 */
    @Test
    fun parsesAllChaptersFromFullWorkPage() {
        assertEquals(37, detail.chapters.size)
        assertEquals("页面声明的章数", 37, detail.postedChapters)
        assertFalse("37/? 是连载中", detail.complete)
    }

    /** 章标题形如 `<a>Chapter 1</a>: Quebrado`，取完整可见文本 */
    @Test
    fun chapterTitlesCarryName() {
        assertEquals("Chapter 1: Quebrado", detail.chapters[0].title)
        assertEquals("Chapter 2: Todo por sobrevivir", detail.chapters[1].title)
    }

    @Test
    fun chapterTextHasNoMarkupLeft() {
        // 正文里的 < 可能是作者打的爱心「<3」，合法内容；只断言没有真正的标签形态
        val tag = Regex("""</?[a-zA-Z][^>]*>""")
        detail.chapters.forEach { chapter ->
            assertFalse("正文残留标签", tag.containsMatchIn(chapter.text))
            assertFalse("正文残留实体", chapter.text.contains("&amp;") || chapter.text.contains("&#39;"))
        }
    }

    @Test
    fun paragraphsBecomeNewlines() {
        assertTrue("正文应含换行", detail.fullText.contains("\n"))
        assertTrue("拼出的正文应有长度", detail.fullText.length > 1000)
    }

    /** 统计块（dl.stats）：fixture 的真实值，解析错一个字段这条就红 */
    @Test
    fun parsesStatsBlock() {
        assertEquals(59285, detail.words)
        assertEquals(117, detail.comments)
        assertEquals(469, detail.kudos)
        assertEquals(18, detail.bookmarks)
        assertEquals(6202, detail.hits)
        assertEquals("2019-08-15", detail.publishedAt)
        assertEquals("2026-10-11", detail.updatedAt)
        assertEquals("Español", detail.language)
    }

    @Test
    fun parsesGroupedTags() {
        assertEquals("Explicit", detail.rating)
        assertEquals(listOf("Creator Chose Not To Use Archive Warnings"), detail.warnings)
        assertEquals(listOf("Yuri!!! on Ice (Anime)"), detail.fandoms)
        assertEquals(7, detail.relationships.size)
        assertTrue("关系标签应含主配对", detail.relationships.contains("Katsuki Yuuri/Victor Nikiforov"))
        assertTrue("& 实体应解码", detail.relationships.any { it.contains(" & ") })
    }

    @Test
    fun tagsFollowAo3DisplayOrder() {
        // 详情标签流 = 关系 + 角色 + 自由标签；警告/分级/分类单列
        val expected = detail.relationships + detail.characters + detail.freeforms
        assertEquals(expected, detail.tags)
        assertTrue("标签流不应混入分级", detail.tags.none { it == "Explicit" })
    }

    @Test
    fun adultFlagFollowsRating() {
        assertTrue(detail.isAdult)
    }

    @Test
    fun summaryIsDecoded() {
        assertTrue("摘要应解析出来", detail.summary.isNotBlank())
        assertFalse("摘要残留标签", detail.summary.contains("<"))
    }

    /** 未知/畸形页面必须给 NotFound，不能静默返回空详情 */
    @Test
    fun throwsNotFoundOnUnrecognizablePage() {
        listOf(
            "<html><body><div class='unrelated'>no work here</div></body></html>",
            "",
            "<html><body><h2 class=\"title heading\">Error</h2></body></html>",
        ).forEach { page ->
            try {
                Ao3WorkParser.parse(page, 1L)
                throw AssertionError("应抛 NotFound: ${page.take(40)}")
            } catch (e: AppError.NotFound) {
                // 预期
            }
        }
        try {
            Ao3WorkParser.parse(fullHtml, 0L)
            throw AssertionError("id 非法应抛 NotFound")
        } catch (e: AppError.NotFound) {
            // 预期
        }
    }

    @Test
    fun chapterCountMatchesParsedChapters() {
        assertEquals(37, Ao3WorkParser.chapterCount(fullHtml))
    }

    /**
     * 成人内容警告页不是作品页。fixture 是应用里实抓的（同一作品的 `/works/{id}`，
     * 没带 `view_adult=true` 时站点回的就是它，HTTP 200）：只有一段警告与一个
     * 「Yes, Continue」链接，正文与 work meta 都没有——解析器必须报 NotFound，
     * 不能把页脚那张作品卡片当成详情收下。
     */
    @Test
    fun adultGatePageIsNotAWorkPage() {
        val gate = readResource("ao3/adult_gate.html")
        assertTrue("fixture 应是警告页（含 Yes, Continue）", gate.contains("Yes, Continue"))
        try {
            Ao3WorkParser.parse(gate, 20251606L)
            throw AssertionError("成人警告页应抛 NotFound")
        } catch (e: AppError.NotFound) {
            // 预期
        }
    }

    /**
     * 正文开头的读屏标签不能进正文。多章页每章正文是
     * `<div class="userstuff module" role="article"><h3 class="landmark">Chapter Text</h3>…`，
     * 剥标签的实现会让每章都以「Chapter Text」开头（实测线上）。
     */
    @Test
    fun chapterLandmarkIsStripped() {
        detail.chapters.forEachIndexed { index, chapter ->
            assertFalse(
                "第 ${index + 1} 章以读屏标签开头: ${chapter.text.take(30)}",
                chapter.text.startsWith("Chapter Text"),
            )
        }
    }

    /**
     * 单章作品（oneshot）的正文结构与多章页不同：`<div id="chapters" role="article">` 里
     * 包着裸 `div.userstuff`（不带 module/role），标签是「Work Text:」。
     * 只认 `userstuff module` 的实现在这里会解析出空正文。
     */
    @Test
    fun parsesOneshotPlainBody() {
        val oneshot = """
            <html><body>
            <h2 class="title heading">A Short One</h2>
            <h3 class="byline heading"><a rel="author" href="/users/someone/pseuds/someone">someone</a></h3>
            <div class="summary module"><h3 class="heading">Summary:</h3>
              <blockquote class="userstuff"><p>This summary must not leak into the body.</p></blockquote>
            </div>
            <dl class="work meta group">
              <dt class="rating tags">Rating:</dt><dd class="rating tags"><ul class="commas"><li><a class="tag" href="/tags/General/works">General Audiences</a></li></ul></dd>
              <dt class="language">Language:</dt><dd class="language">English</dd>
            </dl>
            <dl class="stats">
              <dt class="words">Words:</dt><dd class="words">1,234</dd>
              <dt class="chapters">Chapters:</dt><dd class="chapters">1/1</dd>
            </dl>
            <div id="chapters" role="article">
              <h3 class="landmark heading" id="work">Work Text:</h3>
              <div class="userstuff"><p>The actual body starts here.</p><p>Second paragraph.</p></div>
            </div>
            </body></html>
        """.trimIndent()

        val detail = Ao3WorkParser.parse(oneshot, 347350L)
        assertEquals(listOf("someone"), listOf(detail.authorName))
        assertEquals(1, detail.postedChapters)
        assertEquals(1, detail.plannedChapters)
        assertTrue("单章应判完结", detail.complete)
        assertFalse("General Audiences 不是成人", detail.isAdult)
        assertEquals(1, detail.chapters.size)
        assertTrue("正文应解析出来: 「${detail.chapters[0].text}」", detail.chapters[0].text.contains("The actual body starts here"))
        assertFalse("读屏标签混进正文", detail.chapters[0].text.contains("Work Text"))
        assertFalse("摘要混进正文", detail.chapters[0].text.contains("This summary must not leak"))
        assertFalse("摘要仍应能单独解析", detail.summary.contains("<"))
    }

    /** 咬人：单章正文不能靠"整页剥标签"实现，否则摘要/注记会一起进来 */
    @Test
    fun oneshotBodyIsScopedToTheBodyDiv() {
        val oneshot = """
            <h2 class="title heading">T</h2>
            <h3 class="byline heading"><a rel="author" href="/users/u/pseuds/u">u</a></h3>
            <div id="chapters" role="article"><h3 class="landmark heading">Work Text:</h3>
              <div class="userstuff"><p>Body only.</p></div>
            </div>
        """.trimIndent()
        val text = Ao3WorkParser.parse(oneshot, 1L).chapters.single().text
        assertEquals("Body only.", text)
    }

    @Test
    fun workUrlIsBuiltFromId() {
        assertEquals("https://archiveofourown.org/works/20251606", ao3WorkUrl(20251606L))
        assertEquals("https://archiveofourown.org/users/KatsudonKo", ao3UserUrl("KatsudonKo"))
    }

    @Test
    fun parseIsDeterministic() {
        assertEquals(detail, Ao3WorkParser.parse(fullHtml, 20251606L))
    }
}
