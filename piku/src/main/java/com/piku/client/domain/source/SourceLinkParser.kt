package com.piku.client.domain.source

import com.piku.client.domain.model.WorkKind
import com.piku.client.domain.model.WorkSource

interface SourceLinkParser {

    val sourceId: WorkSource
    val hosts: Set<String>
    fun parse(url: String): SourceLink?
}

sealed interface SourceLink {
    data class Work(
        val source: WorkSource,
        val workId: Long,
        val kind: WorkKind = WorkKind.ILLUST,
        val authorId: Long = 0,
    ) : SourceLink

    data class User(val source: WorkSource, val userId: Long) : SourceLink
}

class SourceLinkResolver(parsers: List<SourceLinkParser>) {

    private val parsers = parsers.toList()

    fun parse(url: String): SourceLink? = parsers.firstNotNullOfOrNull { it.parse(url) }
}

internal const val MAX_LINK_LENGTH = 500
