package com.piku.client.ui.search

/** 解析结果：作品链接 / 作者链接 */
sealed interface PoipikuLink {
    data class Work(val authorId: Long, val workId: Long) : PoipikuLink
    data class User(val userId: Long) : PoipikuLink
}

/** 防滥用：超长输入不解析，避免正则回溯/误判 */
private const val MAX_LINK_LENGTH = 500

/**
 * 解析 poipiku 链接：
 * - `poipiku.com/{authorId}/{workId}.html`（作品，authorId/workId 为数字）
 * - `poipiku.com/{userId}/` 或 `poipiku.com/{userId}.html`（作者）
 * 容忍 scheme 缺失、www/m 子域、尾部 `/`、`.html` 后缀与查询/锚点；其余返回 null（走普通搜索）。
 */
fun parsePoipikuLink(raw: String): PoipikuLink? {
    val input = raw.trim()
    if (input.isEmpty() || input.length > MAX_LINK_LENGTH) return null

    // 去掉查询串与锚点后校验主体
    val path = input.substringBefore('?').substringBefore('#')
    val match = Regex(
        """^(?:https?://)?(?:www\.|m\.)?poipiku\.com/(\d+)(?:/(\d+))?(?:\.html)?/?$""",
        RegexOption.IGNORE_CASE,
    ).matchEntire(path) ?: return null

    val authorOrUserId = match.groupValues[1].toLong()
    val workId = match.groupValues[2]
    return if (workId.isEmpty()) {
        PoipikuLink.User(userId = authorOrUserId)
    } else {
        PoipikuLink.Work(authorId = authorOrUserId, workId = workId.toLong())
    }
}