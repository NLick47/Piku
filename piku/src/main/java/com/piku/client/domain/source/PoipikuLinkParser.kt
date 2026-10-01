package com.piku.client.domain.source

import com.piku.client.domain.model.WorkSource
import javax.inject.Inject

class PoipikuLinkParser @Inject constructor() : SourceLinkParser {

    override val sourceId = WorkSource.POIPIKU

    override val hosts = setOf("poipiku.com", "www.poipiku.com", "m.poipiku.com")

    override fun parse(url: String): SourceLink? = parsePoipikuLink(url)
}

private val LINK = Regex(
    """^(?:https?://)?(?:www\.|m\.)?poipiku\.com/(\d+)(?:/(\d+))?(?:\.html)?/?$""",
    RegexOption.IGNORE_CASE,
)

/**
 * 解析 poipiku 链接：
 * - `poipiku.com/{authorId}/{workId}.html`（作品，authorId/workId 为数字）
 * - `poipiku.com/{userId}/` 或 `poipiku.com/{userId}.html`（作者）
 * 容忍 scheme 缺失、www/m 子域、尾部 `/`、`.html` 后缀与查询/锚点；其余返回 null（走普通搜索）。
 */
fun parsePoipikuLink(raw: String): SourceLink? {
    val input = raw.trim()
    if (input.isEmpty() || input.length > MAX_LINK_LENGTH) return null

    // 去掉查询串与锚点后校验主体
    val path = input.substringBefore('?').substringBefore('#')
    val match = LINK.matchEntire(path) ?: return null

    // 超出 Long 范围的数字段按「不是链接」拒绝：toLong() 会抛 NumberFormatException，
    // 而这个解析在搜索框每次键入都会执行
    val authorOrUserId = match.groupValues[1].toLongOrNull() ?: return null
    val workId = match.groupValues[2]
    return if (workId.isEmpty()) {
        SourceLink.User(source = WorkSource.POIPIKU, userId = authorOrUserId)
    } else {
        SourceLink.Work(
            source = WorkSource.POIPIKU,
            workId = workId.toLongOrNull() ?: return null,
            authorId = authorOrUserId,
        )
    }
}
