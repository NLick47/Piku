package com.piku.client.source.ao3.data.remote

import com.piku.client.core.model.AppError

object Ao3WorkParser {

    private val TITLE = Regex("""<h2[^>]*class="[^"]*title heading[^"]*"[^>]*>(.*?)</h2>""", RegexOption.DOT_MATCHES_ALL)

    private val AUTHOR = Regex("""<a[^>]*rel="author"[^>]*href="/users/([^/"]+)/pseuds/[^"]*"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)

    /** 摘要：summary module 里的 blockquote。不能从 module div 起匹配——会吞掉后面的区块 */
    private val SUMMARY = Regex("""<div[^>]*class="[^"]*summary module[^"]*"[^>]*>.*?<blockquote[^>]*class="[^"]*userstuff[^"]*"[^>]*>(.*?)</blockquote>""", RegexOption.DOT_MATCHES_ALL)

    private val WORK_META = Regex("""<dl[^>]*class="work meta group"[^>]*>(.*?)<dt[^>]*class="language""", RegexOption.DOT_MATCHES_ALL)

    /** 章节容器：`<div class="chapter" id="chapter-3">`（全文页每章一个） */
    private val CHAPTER_CONTAINER = Regex("""<div[^>]*class="chapter"[^>]*id="chapter-\d+"[^>]*>""")

    /** 章标题：容器内 preface 的 `h3.title`（形如 `<a href="...">Chapter 1</a>: 名字`） */
    private val CHAPTER_TITLE = Regex("""<h3[^>]*class="title"[^>]*>(.*?)</h3>""", RegexOption.DOT_MATCHES_ALL)

    /** 章节正文块：多章全文页每章一个 `div.userstuff.module[role=article]`；非贪婪取到该 div 闭合 */
    private val CHAPTER_TEXT = Regex("""<div[^>]*class="[^"]*userstuff module[^"]*"[^>]*role="article"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)

    /** 兜底一：同类块但页面没给 role */
    private val CHAPTER_TEXT_NO_ROLE = Regex("""<div[^>]*class="[^"]*userstuff module[^"]*"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)

    /**
     * 兜底二：单章页的裸 `div.userstuff`（不带 module/role）。
     * 实测单章作品是 `<div id="chapters" role="article"><h3 landmark>Work Text:</h3>
     * <div class="userstuff"><p>…`。摘要/章注是 `blockquote.userstuff`，不会被这条匹配到。
     */
    private val CHAPTER_TEXT_PLAIN = Regex("""<div[^>]*class="userstuff"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)

    /** 正文块内的读屏标签（`<h3 class="landmark heading">Chapter Text</h3>`）：文字是给屏幕阅读器的，不能进正文 */
    private val LANDMARK = Regex("""<h3[^>]*class="[^"]*landmark[^"]*"[^>]*>.*?</h3>""", RegexOption.DOT_MATCHES_ALL)

    private val WORDS = Regex("""<dd[^>]*class="words"[^>]*>([\d,]+)</dd>""")
    private val CHAPTERS = Regex("""<dd[^>]*class="chapters"[^>]*>([^<]*)</dd>""")
    private val LANGUAGE = Regex("""<dd[^>]*class="language"[^>]*>([^<]*)</dd>""")
    private val PUBLISHED = Regex("""<dd[^>]*class="published"[^>]*>([^<]*)</dd>""")
    private val UPDATED = Regex("""<dd[^>]*class="status"[^>]*>([^<]*)</dd>""")
    private val COMMENTS = Regex("""<dd[^>]*class="comments"[^>]*>([\d,]+)""")
    private val KUDOS = Regex("""<dd[^>]*class="kudos"[^>]*>([\d,]+)""")
    private val BOOKMARKS = Regex("""<dd[^>]*class="bookmarks"[^>]*>(?:<a[^>]*>)?([\d,]+)""")
    private val HITS = Regex("""<dd[^>]*class="hits"[^>]*>([\d,]+)""")

    /** 分组标签块：`dd.rating tags` 等，块内每个 `<a class="tag">` 是一枚标签 */
    private fun group(name: String): Regex =
        Regex("""<dd[^>]*class="[^"]*\b$name tags\b[^"]*"[^>]*>(.*?)</dd>""", RegexOption.DOT_MATCHES_ALL)

    private val GROUP_RATING = group("rating")
    private val GROUP_WARNING = group("warning")
    private val GROUP_CATEGORY = group("category")
    private val GROUP_FANDOM = group("fandom")
    private val GROUP_RELATIONSHIP = group("relationship")
    private val GROUP_CHARACTER = group("character")
    private val GROUP_FREEFORM = group("freeform")

    private val TAG_IN_GROUP = Regex("""<a[^>]*class="tag"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)

    /** 一章：标题（"Chapter 1: 名字" 形态，直接取页面文本）+ 正文 */
    data class Ao3Chapter(val title: String, val text: String)

    data class Ao3WorkDetail(
        val title: String,
        val authorName: String,
        val authorUser: String,
        val summary: String,
        val chapters: List<Ao3Chapter>,
        val words: Int,
        /** 页面声明的章数（`dd.chapters` 的 "37/?"） */
        val postedChapters: Int,
        /** `dd.chapters` 的分母：`?` = 未声明（连载中），等于分子 = 完结 */
        val plannedChapters: Int,
        val language: String,
        val rating: String,
        val warnings: List<String>,
        val categories: List<String>,
        val fandoms: List<String>,
        val relationships: List<String>,
        val characters: List<String>,
        val freeforms: List<String>,
        val publishedAt: String,
        val updatedAt: String,
        val comments: Int,
        val kudos: Int,
        val bookmarks: Int,
        val hits: Int,
    ) {
        val isAdult: Boolean get() = rating.equals("Explicit", ignoreCase = true) ||
            rating.equals("Mature", ignoreCase = true)

        /** 完结 = 计划章数已知且已全部贴出 */
        val complete: Boolean get() = plannedChapters in 1..postedChapters

        /** 全部章节拼成一篇正文，章节间空行分隔（novelBody 的兜底路径用） */
        val fullText: String get() = chapters.filter { it.text.isNotBlank() }.joinToString("\n\n") { it.text }

        /** 详情页标签流：按 AO3 的展示顺序（关系/角色/自由标签），警告与分类单列 */
        val tags: List<String> get() = relationships + characters + freeforms
    }

    /**
     * 解析详情页。页面结构完全不认（404 页/错误页）时抛 [AppError.NotFound]。
     */
    fun parse(html: String, workId: Long): Ao3WorkDetail {
        if (workId <= 0L) throw AppError.NotFound
        val title = TITLE.find(html)?.groupValues?.get(1)?.let { cleanText(it) }
            ?: throw AppError.NotFound
        val authorMatch = AUTHOR.find(html)
        val authorName = authorMatch?.groupValues?.get(2)?.let { cleanText(it) }.orEmpty()
        val authorUser = authorMatch?.groupValues?.get(1).orEmpty()

        val chapters = parseChapters(html)
        if (chapters.isEmpty() && authorName.isEmpty()) throw AppError.NotFound

        val meta = WORK_META.find(html)?.groupValues?.get(1).orEmpty()
        val (posted, planned) = parseChapterCount(CHAPTERS.find(html)?.groupValues?.get(1))

        return Ao3WorkDetail(
            title = title,
            authorName = authorName,
            authorUser = authorUser,
            summary = SUMMARY.find(html)?.groupValues?.get(1)?.let { cleanText(it) }.orEmpty(),
            chapters = chapters,
            words = WORDS.find(html)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0,
            postedChapters = posted,
            plannedChapters = planned,
            language = LANGUAGE.find(html)?.groupValues?.get(1)?.let { cleanText(it) }.orEmpty(),
            rating = tagsOf(meta, GROUP_RATING).firstOrNull().orEmpty(),
            warnings = tagsOf(meta, GROUP_WARNING),
            categories = tagsOf(meta, GROUP_CATEGORY),
            fandoms = tagsOf(meta, GROUP_FANDOM),
            relationships = tagsOf(meta, GROUP_RELATIONSHIP),
            characters = tagsOf(meta, GROUP_CHARACTER),
            freeforms = tagsOf(meta, GROUP_FREEFORM),
            publishedAt = PUBLISHED.find(html)?.groupValues?.get(1)?.let { cleanText(it) }.orEmpty(),
            updatedAt = UPDATED.find(html)?.groupValues?.get(1)?.let { cleanText(it) }.orEmpty(),
            comments = COMMENTS.find(html)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0,
            kudos = KUDOS.find(html)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0,
            bookmarks = BOOKMARKS.find(html)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0,
            hits = HITS.find(html)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0,
        )
    }

    /** "37/?" → (37, 0)；"1/1" → (1, 1)；缺失 → (0, 0) */
    private fun parseChapterCount(raw: String?): Pair<Int, Int> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return 0 to 0
        val posted = text.substringBefore('/').trim().toIntOrNull() ?: 0
        val planned = text.substringAfter('/', "").trim().toIntOrNull() ?: 0
        return posted to planned
    }

    /**
     * 收集一章的标题与正文。优先按章容器切分（标题与正文同块，配对不会错位）；
     * 页面上没有章容器时退回整页单篇（oneshot / 单章页）。
     */
    private fun parseChapters(html: String): List<Ao3Chapter> {
        val containers = splitChapterContainers(html)
        if (containers.isNotEmpty()) {
            return containers.map { block ->
                val title = CHAPTER_TITLE.find(block)?.groupValues?.get(1)
                    ?.let { cleanText(it) }
                    .orEmpty()
                Ao3Chapter(title = title, text = chapterBody(block))
            }.filter { it.text.isNotBlank() }
        }
        return listOf(Ao3Chapter(title = "", text = chapterBody(html)))
            .filter { it.text.isNotBlank() }
    }

    private fun splitChapterContainers(html: String): List<String> {
        val starts = CHAPTER_CONTAINER.findAll(html).toList()
        if (starts.isEmpty()) return emptyList()
        return starts.mapIndexed { index, match ->
            val from = match.range.first
            val to = if (index + 1 < starts.size) starts[index + 1].range.first else html.length
            html.substring(from, to)
        }
    }

    private fun chapterBody(block: String): String {
        val raw = CHAPTER_TEXT.find(block)?.groupValues?.get(1)
            ?: CHAPTER_TEXT_NO_ROLE.find(block)?.groupValues?.get(1)
            ?: CHAPTER_TEXT_PLAIN.find(block)?.groupValues?.get(1)
            ?: ""
        return cleanText(LANDMARK.replace(raw, ""))
    }

    private fun tagsOf(metaBlock: String, pattern: Regex): List<String> =
        pattern.find(metaBlock)?.groupValues?.get(1)
            ?.let { group -> TAG_IN_GROUP.findAll(group).map { cleanText(it.groupValues[1]) }.toList() }
            .orEmpty()

    /** 供详情页判断是否有多章（阅读器据此展示章节导航） */
    fun chapterCount(html: String): Int = parseChapters(html).size

    internal fun cleanText(raw: String): String = Ao3Html.cleanText(raw)

    internal fun decodeEntities(raw: String): String = Ao3Html.decodeEntities(raw)

}

/** 构造 AO3 作品页 URL（复制链接、出站浏览器用） */
fun ao3WorkUrl(workId: Long): String = "https://${Ao3Config.MAIN_HOST}/works/$workId"

/** 构造 AO3 作者页 URL */
fun ao3UserUrl(user: String): String = "https://${Ao3Config.MAIN_HOST}/users/$user"
