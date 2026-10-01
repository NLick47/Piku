package com.piku.client.domain.source

import com.piku.client.domain.model.WorkKind
import com.piku.client.domain.model.WorkSource
import javax.inject.Inject

class PixivLinkParser @Inject constructor() : SourceLinkParser {

    override val sourceId = WorkSource.PIXIV

    override val hosts = setOf("pixiv.net", "www.pixiv.net")

    override fun parse(url: String): SourceLink? = parsePixivLink(url)
}

private val NOVEL_PATH = Regex(
    """^(?:https?://)?(?:www\.)?pixiv\.net(?:/en)?/novel/show\.php$""",
    RegexOption.IGNORE_CASE,
)

private val PATH = Regex(
    """^(?:https?://)?(?:www\.)?pixiv\.net(?:/en)?/(artworks|users)/(\d+)(?:/artworks)?/?$""",
    RegexOption.IGNORE_CASE,
)

/**
 * 解析 pixiv 链接：
 * - `pixiv.net/artworks/{id}`（插画/漫画，容忍 /en 前缀）
 * - `pixiv.net/novel/show.php?id={id}`（小说；id 在 query 里，manifest 的 path 匹配不含 query）
 * - `pixiv.net/users/{id}[/artworks]`（作者；artworks 后缀是画师插画 tab 的规范地址）
 * 容忍 scheme 缺失、www 子域、尾部 `/` 与锚点；legacy 形态（member_illust.php、/u/{id}）不支持。
 */
fun parsePixivLink(raw: String): SourceLink? {
    val input = raw.trim()
    if (input.isEmpty() || input.length > MAX_LINK_LENGTH) return null

    val noAnchor = input.substringBefore('#')
    val path = noAnchor.substringBefore('?')

    // 小说页的 path 恒为 novel/show.php，id 从 query 串里取
    if (NOVEL_PATH.matchEntire(path) != null) {
        val novelId = noAnchor.substringAfter('?', "")
            .split('&')
            .firstOrNull { it.startsWith("id=") }
            ?.substringAfter('=')
            ?.toLongOrNull()
            ?: return null
        return SourceLink.Work(WorkSource.PIXIV, novelId, WorkKind.NOVEL)
    }

    val match = PATH.matchEntire(path) ?: return null
    val id = match.groupValues[2].toLongOrNull() ?: return null
    return when (match.groupValues[1].lowercase()) {
        "artworks" -> SourceLink.Work(WorkSource.PIXIV, id)
        else -> SourceLink.User(WorkSource.PIXIV, id)
    }
}
