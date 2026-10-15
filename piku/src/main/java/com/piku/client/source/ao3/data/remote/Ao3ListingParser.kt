package com.piku.client.source.ao3.data.remote

import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkKind
import com.piku.client.core.model.WorkSource

/**
 * AO3 作品列表页解析（搜索结果、标签页、用户页同一种 blurb 结构）。
 *
 * 条目是 `<li id="work_123" class="work blurb group work-123 user-456 ...">`。
 * class 是动态拼接的：多作者时带多个 `user-{id}`，还有 `own` 等标记。
 * 所以不能精确匹配 `class="work blurb group"`（实测 grep -c 为 0），
 * 用 `work blurb group` 子串定位块首，块尾取 `</li>`。
 */
object Ao3ListingParser {

    /** 块首：`<li ... class="... work blurb group ...">` */
    private val BLURB_START = Regex("""<li[^>]*class="[^"]*\bwork blurb group\b[^"]*"[^>]*>""")

    /** 块首自带的条目 id：`id="work_20251606"`；块首无 id 时从标题链接兜底取 */
    private val WORK_ID = Regex("""id="work_(\d+)"""")

    private val TITLE_LINK = Regex("""<h4[^>]*class="[^"]*heading[^"]*"[^>]*>\s*<a[^>]*href="/works/(\d+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)

    private val AUTHOR = Regex("""<a[^>]*rel="author"[^>]*href="/users/([^/"]+)/pseuds/[^"]*"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)

    /** 匿名作品没有 rel=author 链接，作者区是裸文本 `Anonymous` */
    private val ANONYMOUS = Regex("""<h4[^>]*class="[^"]*heading[^"]*"[^>]*>(?:(?!</h4>).)*?\bAnonymous\b""", RegexOption.DOT_MATCHES_ALL)

    private val FANDOM = Regex("""<h5[^>]*class="[^"]*fandoms[^"]*heading[^"]*"[^>]*>(.*?)</h5>""", RegexOption.DOT_MATCHES_ALL)

    /** 同人区里的标签：`<a class="tag" href="/tags/…/works">Fandom Name</a>` */
    private val TAG_IN_BLOCK = Regex("""<a[^>]*class="tag"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)

    /** required-tags 里的分级：`<span class="rating-explicit rating" title="Explicit">` */
    private val RATING = Regex("""class="rating-([a-z-]+) rating\"""")

    /** 完成状态：`complete-no iswip`（连载中）/ `complete-yes` */
    private val COMPLETE = Regex("""class="(complete-(?:yes|no)) iswip"""")

    private val SUMMARY = Regex("""<blockquote[^>]*class="[^"]*userstuff summary[^"]*"[^>]*>(.*?)</blockquote>""", RegexOption.DOT_MATCHES_ALL)

    private val LANGUAGE = Regex("""<dd[^>]*class="language"[^>]*>([^<]*)</dd>""")

    /** 统计：`<dt class="words">Words:</dt><dd class="words">59,285</dd>` */
    private val WORDS = Regex("""<dd[^>]*class="words"[^>]*>([\d,]+)</dd>""")

    /** kudos 为零时站点不输出这一项，取不到按 0；有值时值被 `<a href="…#kudos">` 包着 */
    private val KUDOS = Regex("""<dd[^>]*class="kudos"[^>]*>(?:<a[^>]*>)?([\d,]+)""")

    /** 标签区：`<ul class="tags commas">`（关系/角色/自由标签，按站点顺序） */
    private val TAGS_BLOCK = Regex("""<ul[^>]*class="tags commas"[^>]*>(.*?)</ul>""", RegexOption.DOT_MATCHES_ALL)

    /** 两条样板警告：每篇都有或几乎都有，摆到卡片的标签位等于没信息 */
    private val BOILERPLATE_WARNINGS = setOf(
        "No Archive Warnings Apply",
        "Creator Chose Not To Use Archive Warnings",
    )

    /**
     * 下一分页链接。两种形态都要认：
     * 搜索页 `<li class="next" title="next"><a rel="next" href="…">Next →</a>`（带 rel），
     * 标签页 `<li class="next"><a href="…">Next →</a>`（不带 rel，2026-10 实测）。
     * 末页是 `<li class="next"><span class="disabled">…`——要求紧跟 `<a>` 才不会把末页认成有下一页。
     */
    private val NEXT_PAGE = Regex("""<li[^>]*class="[^"]*\bnext\b[^"]*"[^>]*>\s*<a[^>]*>""", RegexOption.DOT_MATCHES_ALL)

    fun parse(html: String): List<Work> {
        val blocks = splitBlurbs(html)
        return blocks.mapNotNull { block -> parseBlurb(block) }
    }

    /** 当前页之后是否还有页。AO3 的分页控件带 `rel="next"` 时才存在下一页 */
    fun hasNextPage(html: String): Boolean = NEXT_PAGE.containsMatchIn(html)

    private fun splitBlurbs(html: String): List<String> {
        val starts = BLURB_START.findAll(html).toList()
        if (starts.isEmpty()) return emptyList()
        return starts.mapIndexed { index, match ->
            val from = match.range.first
            val to = if (index + 1 < starts.size) starts[index + 1].range.first else html.length
            html.substring(from, minOf(to, html.length))
        }
    }

    private fun parseBlurb(block: String): Work? {
        val titleMatch = TITLE_LINK.find(block) ?: return null
        val workId = titleMatch.groupValues[1].toLongOrNull()
            ?: WORK_ID.find(block)?.groupValues?.get(1)?.toLongOrNull()
            ?: return null
        val title = cleanText(titleMatch.groupValues[2])
        if (title.isEmpty()) return null

        val authorMatch = AUTHOR.find(block)
        val isAnonymous = authorMatch == null && ANONYMOUS.containsMatchIn(block)
        val authorName = when {
            authorMatch != null -> cleanText(authorMatch.groupValues[2])
            isAnonymous -> "Anonymous"
            else -> ""
        }
        // AO3 的作者是用户名（字符串），没有数字 id。本地收藏/历史要求 Long，
        // 用用户名 hashCode 兜一个稳定 id——同一作者在任何列表里都拿到同一个值。
        val authorId = authorMatch?.groupValues?.get(1)?.hashCode()?.toLong()?.and(0x7fffffffL)
            ?: 0L

        // 同人只取 h5 里的标签文本：整段剥标签会把读屏用的 "Fandoms:" 也带进来
        val fandoms = FANDOM.find(block)?.groupValues?.get(1)
            ?.let { region -> TAG_IN_BLOCK.findAll(region).map { cleanText(it.groupValues[1]) }.toList() }
            .orEmpty()

        val rating = RATING.find(block)?.groupValues?.get(1).orEmpty()
        val complete = COMPLETE.find(block)?.groupValues?.get(1).orEmpty()
        val summary = SUMMARY.find(block)?.groupValues?.get(1)?.let { cleanText(it) }.orEmpty()
        val words = WORDS.find(block)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
        val kudos = KUDOS.find(block)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
        val tags = TAGS_BLOCK.find(block)?.groupValues?.get(1)
            ?.let { region -> TAG_IN_BLOCK.findAll(region).map { cleanText(it.groupValues[1]) }.toList() }
            .orEmpty()
            // 两条样板警告谁都有，占着卡片的标签位什么也没说
            .filterNot { it in BOILERPLATE_WARNINGS }

        return Work(
            id = workId,
            authorId = authorId,
            authorName = authorName,
            authorAvatarUrl = null,
            // AO3 没有分类体系，用 fandoms 第一项充当分类名（卡片与详情据此显示来源）
            categoryCd = 0,
            categoryName = fandoms.firstOrNull().orEmpty(),
            title = title,
            // AO3 无缩略图：列表按单列 blurb 渲染（TextWorkBlurb），不占位图
            thumbnailUrl = "",
            thumbWidth = 0,
            thumbHeight = 0,
            imageCount = 0,
            textLength = words,
            r18 = rating == "explicit" || rating == "mature",
            // AO3 的 archive warning（Major Character Death 等）统一按警告处理
            warning = block.contains("warning-") && !block.contains("warning-choosenotto"),
            loginRequired = false,
            isPrivate = false,
            source = WorkSource.AO3,
            kind = WorkKind.NOVEL,
            inProgress = complete == "complete-no",
            summary = summary,
            tags = tags,
            likes = kudos,
        )
    }

    internal fun cleanText(raw: String): String = Ao3Html.cleanText(raw)

    internal fun decodeEntities(raw: String): String = Ao3Html.decodeEntities(raw)

}
