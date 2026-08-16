package com.piku.client.common

/**
 * 富文本与链接标记之间的纯转换逻辑，供解析层（生成标记）与 UI 层（渲染链接）共享。
 *
 * 标记协议：`[url]显示文本[/url]`。URL 只识别 http(s)，非 http 链接（如内部路径、
 * javascript:）在转换时只保留文本，不生成标记。
 */

/** 一个文本段：纯文本，或可点击链接（URL + 显示文本） */
sealed interface LinkSegment {
    data class Plain(val text: String) : LinkSegment
    data class Link(val url: String, val text: String) : LinkSegment
}

object LinkText {

    /**
     * 把含 HTML 的文本转换为标记化纯文本：
     * 1. `<a>`（href 双/单引号均可）→ `[url]text[/url]` 标记
     * 2. 剥离残留的 `<a>`、`</a>`、`<img>` 标签（未闭合/孤立锚兜底）
     *
     * 不处理 `<br>` 与 HTML 实体，由调用方按需处理。
     */
    fun convert(html: String): String {
        var result = html
        while (true) {
            val match = REGEX_ANCHOR.find(result) ?: break
            val url = match.groupValues[1].trim()
            val replacement = if (url.startsWith("http")) {
                val text = match.groupValues[2]
                "[$url]${text.ifBlank { url }}[/$url]"
            } else {
                match.groupValues[2]
            }
            result = result.replaceRange(match.range, replacement)
        }
        return result.replace(REGEX_STRAY_TAGS, "")
    }

    /** HTML 实体解码 */
    fun decodeEntities(text: String): String =
        text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")

    /**
     * 把标记化文本解析为段序列：
     * - 完整标记 `[url]text[/url]` → Link
     * - 未闭合标记 `[url]text`（如跨行锚被切分后的残段）→ Link，不显示原文
     * - 裸 URL → Link（显示文本即 URL，尾部标点修剪）
     * - 孤立的 `[/url]` 残段 → 丢弃
     */
    fun parse(text: String): List<LinkSegment> {
        val segments = mutableListOf<LinkSegment>()
        var index = 0
        for (match in REGEX_LINK_PATTERN.findAll(text)) {
            appendBareUrls(segments, text.substring(index, match.range.first))
            val url = match.groupValues[1].trim()
            val display = match.groupValues[2].ifBlank { url }
            segments.add(LinkSegment.Link(url, display))
            index = match.range.last + 1
        }
        appendBareUrls(segments, text.substring(index))
        return segments
    }

    private fun appendBareUrls(segments: MutableList<LinkSegment>, raw: String) {
        val cleaned = raw.replace(REGEX_STRAY_CLOSE, "")
        if (cleaned.isEmpty()) return
        var index = 0
        for (match in REGEX_URL.findAll(cleaned)) {
            if (match.range.first > index) {
                segments.add(LinkSegment.Plain(cleaned.substring(index, match.range.first)))
            }
            val url = match.value.trimEnd(*URL_TRAILING)
            segments.add(LinkSegment.Link(url, url))
            index = match.range.first + url.length
        }
        if (index < cleaned.length) {
            segments.add(LinkSegment.Plain(cleaned.substring(index)))
        }
    }

    private val REGEX_ANCHOR =
        Regex(
            """<a\b[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )

    private val REGEX_STRAY_TAGS =
        Regex("""<a\b[^>]*>|</a\s*>|<img\b[^>]*>""", RegexOption.IGNORE_CASE)

    /** 只匹配 URL 允许字符（RFC 3986 未保留 + 保留字符），避免吞掉紧随的中文标点与文字 */
    private val REGEX_URL = Regex("""https?://[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+""")

    /** 匹配完整标记 `[url]...[/url]`，也兜底匹配未闭合标记 `[url]...`（到文本结尾） */
    private val REGEX_LINK_PATTERN =
        Regex(
            """\[(https?://[^\]]+)\](.*?)(?:\[/https?://[^\]]+\]|$)""",
            RegexOption.DOT_MATCHES_ALL,
        )

    private val REGEX_STRAY_CLOSE = Regex("""\[/https?://[^\]]+\]""")

    private val URL_TRAILING = charArrayOf(
        '.', ',', '，', '。', '、', ';', '；', '!', '！', '?', '？', ')', '）', '」', '』', ']', '】',
    )
}