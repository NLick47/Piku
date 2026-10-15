package com.piku.client.core.ui.feature.tags

import com.piku.client.core.model.AppError
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
        searchRegistry.byIdOrNull(source)?.let { plugin ->
            return plugin.searchTagWorks(tag, emptyMap(), page).map { it.items }
        }
        // 只有 poipiku 允许落到内置链路；未登记的源明确"没有内容"，不静默串别源的作品
        if (source != WorkSource.POIPIKU) return Result.failure(AppError.NotFound)
        return poipikuFallback(tag, page)
    }
}
