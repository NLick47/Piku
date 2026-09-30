package com.piku.client.domain.source

import com.piku.client.domain.model.WorkSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceRegistry @Inject constructor(
    sources: Set<@JvmSuppressWildcards ContentSource>,
) {
    private val byId: Map<WorkSource, ContentSource> = sources.associateBy { it.id }

    fun byId(id: WorkSource): ContentSource =
        byId[id] ?: error("未注册的内容源: $id")

    /** 供换源控件列出可选源 */
    val all: List<ContentSource> get() = byId.values.toList()
}
