package com.piku.client.source.ao3.data.link

import com.piku.client.core.model.WorkKind
import com.piku.client.core.model.WorkSource
import com.piku.client.core.source.MAX_LINK_LENGTH
import com.piku.client.core.source.SourceLink
import com.piku.client.core.source.SourceLinkParser
import javax.inject.Inject

class Ao3LinkParser @Inject constructor() : SourceLinkParser {

    override val sourceId = WorkSource.AO3

    override val hosts = setOf("archiveofourown.org", "www.archiveofourown.org")

    override fun parse(url: String): SourceLink? = parseAo3Link(url)
}

private val LINK = Regex(
    """^(?:https?://)?(?:www\.)?archiveofourown\.org/works/(\d+)/?(?:[?#].*)?$""",
    RegexOption.IGNORE_CASE,
)

private val CHAPTER_LINK = Regex(
    """^(?:https?://)?(?:www\.)?archiveofourown\.org/works/(\d+)/chapters/\d+/?(?:[?#].*)?$""",
    RegexOption.IGNORE_CASE,
)

/**
 * 解析 AO3 链接：
 * - `archiveofourown.org/works/{id}`（作品；`?view_full_work=true` 一并容忍）
 * - `archiveofourown.org/works/{id}/chapters/{n}`（单章，归并到同一作品）
 * 其余（用户页、标签页）返回 null，走普通搜索。
 */
fun parseAo3Link(raw: String): SourceLink? {
    val input = raw.trim()
    if (input.isEmpty() || input.length > MAX_LINK_LENGTH) return null

    val match = LINK.matchEntire(input) ?: CHAPTER_LINK.matchEntire(input) ?: return null
    val workId = match.groupValues[1].toLongOrNull() ?: return null
    return SourceLink.Work(
        source = WorkSource.AO3,
        workId = workId,
        kind = WorkKind.NOVEL,
    )
}
