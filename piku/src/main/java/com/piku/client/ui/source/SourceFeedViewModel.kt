package com.piku.client.ui.source

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.ContentSource
import com.piku.client.domain.source.SourceFacet
import com.piku.client.domain.source.SourceFeed
import com.piku.client.domain.model.Work
import com.piku.client.domain.source.SourceRegistry
import com.piku.client.domain.source.SourceWorkOpen
import com.piku.client.domain.usecase.ObserveHomeSourceUseCase
import com.piku.client.ui.home.FeedLoader
import com.piku.client.ui.home.FeedSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SourceFeedConfig(val prefetchEnabled: Boolean = true)

/** 非 poipiku 源的加载键：源 + 流 + 收窄维度。流/维度 id 都来自该源的声明 */
internal data class SourceKey(
    val source: WorkSource,
    val feedId: String,
    val facetId: String?,
)

/**
 * 通用源页面的壳。按 `ContentSource` 的声明渲染 tab/维度/条目，分页交给 [FeedLoader]——
 * 它只透传 feedId/facetId/page，不认识任何具体站点；新增源时本类零改动。
 * poipiku 是主源，由 HomeViewModel 的专属壳负责，不走这里。
 */
@HiltViewModel
class SourceFeedViewModel @Inject constructor(
    private val sourceRegistry: SourceRegistry,
    observeHomeSourceUseCase: ObserveHomeSourceUseCase,
    private val settingsRepository: SettingsRepository,
    private val isLoggedIn: () -> Boolean,
    private val config: SourceFeedConfig,
) : ViewModel() {

    data class UiState(
        val source: WorkSource = WorkSource.POIPIKU,
        /** 当前源的声明，UI 据此渲染 tab 行与维度选择 */
        val feeds: List<SourceFeed> = emptyList(),
        val facets: List<SourceFacet> = emptyList(),
        val feedId: String = "",
        val facetId: String? = null,
        val items: List<Work> = emptyList(),
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val endReached: Boolean = false,
        val failed: Boolean = false,
        val loadMoreFailed: Boolean = false,
        val needLogin: Boolean = false,
        /** 看图器的 R-18 门：与 poipiku 详情的门同开关，但判定在查看器自己这里 */
        val adultEnabled: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var currentKey: SourceKey? = null
    private var currentCollectJob: Job? = null

    /** 每个源记住用户选过的流/维度，会话内切回不重置 */
    private val selections = mutableMapOf<WorkSource, Selection>()

    /** (source, feedId, facetId) → 自治加载器；兼内存缓存 */
    private val loaders = object : LinkedHashMap<SourceKey, FeedLoader<SourceKey, Work>>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<SourceKey, FeedLoader<SourceKey, Work>>,
        ): Boolean {
            if (size <= MAX_LOADERS) return false
            eldest.value.dispose()
            return true
        }
    }

    init {
        viewModelScope.launch {
            observeHomeSourceUseCase().collect { onSourceChanged(it) }
        }
        // R-18 过滤发生在源的取页里：开关一变，缓存里的旧页就脏了，整体失效重建
        viewModelScope.launch {
            settingsRepository.showAdultContent
                .onEach { enabled -> _ui.update { it.copy(adultEnabled = enabled) } }
                .drop(1)
                .collect { invalidateAll() }
        }
    }

    fun selectFeed(feedId: String) {
        val state = _ui.value
        if (feedId == state.feedId) return
        selections[state.source] = Selection(feedId, state.facetId)
        switchLoader()
    }

    fun selectFacet(facetId: String?) {
        val state = _ui.value
        if (facetId == state.facetId) return
        selections[state.source] = Selection(state.feedId, facetId)
        switchLoader()
    }

    /** 点开作品的去向，由当前源的声明决定 */
    fun open(work: Work): SourceWorkOpen = sourceRegistry.byId(_ui.value.source).open(work)

    fun loadMore() = currentLoader()?.loadMore()

    fun retry() = currentLoader()?.refresh(countNotice = false)

    fun retryLoadMore() = currentLoader()?.retryLoadMore()

    private fun onSourceChanged(source: WorkSource) {
        // poipiku 走 HomeViewModel 的专属壳，这里不为它建加载器
        if (source == WorkSource.POIPIKU) {
            _ui.update { it.copy(source = source) }
            return
        }
        _ui.update { it.copy(source = source) }
        switchLoader()
    }

    private fun switchLoader() {
        val source = _ui.value.source
        if (source == WorkSource.POIPIKU) return
        val declaration = sourceRegistry.byId(source)
        val selection = selections[source] ?: Selection(
            // 流无默认标记，声明序即 UI 序，取第一个；维度认 selectedByDefault，都没有就取第一个——
            // 有维度必有选中，UI 显示与取参才不会差一档
            feedId = declaration.feeds.first().id,
            facetId = declaration.facets.firstOrNull { it.selectedByDefault }?.id
                ?: declaration.facets.firstOrNull()?.id,
        )
        selections[source] = selection
        val key = SourceKey(source, selection.feedId, selection.facetId)
        currentKey = key
        val loader = obtainLoader(key, declaration)
        applySnapshot(key, loader.state.value, declaration)
        currentCollectJob?.cancel()
        currentCollectJob = viewModelScope.launch {
            // 守卫：切换后到达的旧快照不得覆盖新流的状态
            loader.state.collect { if (currentKey == key) applySnapshot(key, it, declaration) }
        }
    }

    private fun obtainLoader(key: SourceKey, declaration: ContentSource): FeedLoader<SourceKey, Work> =
        loaders.getOrPut(key) { createLoader(key, declaration) }

    private fun createLoader(key: SourceKey, declaration: ContentSource): FeedLoader<SourceKey, Work> {
        var totalPages: Int? = null
        val loader = FeedLoader(
            key = key,
            feed = declaration.feed(key.feedId),
            scope = viewModelScope,
            fetchPage = { page ->
                if (totalPages != null && page >= totalPages!!) {
                    // 榜单页数已探明：本地短路成空页，省一次注定为空的请求
                    Result.success(emptyList())
                } else {
                    declaration.page(key.feedId, key.facetId, page)
                        .onSuccess { totalPages = it.totalPages }
                        .map { it.items }
                }
            },
            isLoggedIn = isLoggedIn,
            idOf = { it.id },
            prefetchEnabled = config.prefetchEnabled,
        )
        loader.refresh(countNotice = false)
        return loader
    }

    private fun currentLoader(): FeedLoader<SourceKey, Work>? {
        val state = _ui.value
        val key = SourceKey(state.source, state.feedId, state.facetId)
        return loaders[key]
    }

    private fun applySnapshot(
        key: SourceKey,
        snap: FeedSnapshot<Work>,
        declaration: ContentSource,
    ) {
        _ui.update {
            it.copy(
                source = key.source,
                feeds = declaration.feeds,
                facets = declaration.facets,
                feedId = key.feedId,
                facetId = key.facetId,
                items = snap.items,
                loading = snap.loading,
                loadingMore = snap.loadingMore,
                endReached = snap.endReached,
                failed = snap.error != null,
                loadMoreFailed = snap.loadMoreError != null,
                needLogin = snap.needLogin,
            )
        }
    }

    private fun invalidateAll() {
        loaders.values.toList().forEach { it.dispose() }
        loaders.clear()
        switchLoader()
    }

    private data class Selection(val feedId: String, val facetId: String?)

    private companion object {
        const val MAX_LOADERS = 12
    }
}
