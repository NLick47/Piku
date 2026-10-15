package com.piku.client.core.ui.feature.tags

import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkSource
import com.piku.client.core.source.SourceSearchRegistry
import com.piku.client.source.poipiku.domain.usecase.PoipikuTagFeed
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagFeedSource @Inject constructor(
    private val searchRegistry: SourceSearchRegistry,
    private val poipikuFallback: PoipikuTagFeed,
) {

    suspend fun works(source: WorkSource, tag: String, page: Int): Result<List<Work>> {
        val plugin = searchRegistry.byIdOrNull(source) ?: return poipikuFallback(tag, page)
        return plugin.searchTagWorks(tag, emptyMap(), page).map { it.items }
    }
}
