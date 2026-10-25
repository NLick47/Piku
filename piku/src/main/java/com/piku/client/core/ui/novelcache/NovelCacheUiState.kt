package com.piku.client.core.ui.novelcache

import com.piku.client.core.novelcache.NovelCacheSnapshot

enum class NovelCacheAction {
    Cache,
    Update,
    Delete,
}

/** 详情页那一行的状态；[Hidden] = 该作品不该出现缓存行 */
sealed interface NovelCacheUiState {
    data object Hidden : NovelCacheUiState
    data object NotCached : NovelCacheUiState
    data object Caching : NovelCacheUiState
    data object Failed : NovelCacheUiState

    data class Cached(
        val chapterCount: Int,
        val byteSize: Long,
        val fetchedAt: Long,
        /** 远端比本地多的章数；0 = 没更新或不知道 */
        val updateCount: Int,
    ) : NovelCacheUiState
}

fun NovelCacheSnapshot.toUiState(): NovelCacheUiState = NovelCacheUiState.Cached(
    chapterCount = chapterCount,
    byteSize = byteSize,
    fetchedAt = fetchedAt,
    updateCount = updateCount,
)
