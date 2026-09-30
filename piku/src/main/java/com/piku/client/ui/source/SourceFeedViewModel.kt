package com.piku.client.ui.source

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.WorkKey
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.ContentSource
import com.piku.client.domain.source.SourceFacetGroup
import com.piku.client.domain.source.SourceFeed
import com.piku.client.domain.model.Work
import com.piku.client.domain.source.ShellFavorites
import com.piku.client.domain.source.SourceLogin
import com.piku.client.domain.source.SourceRegistry
import com.piku.client.domain.source.SourceWorkOpen
import com.piku.client.domain.usecase.ObserveHomeSourceUseCase
import com.piku.client.ui.home.FeedLoader
import com.piku.client.ui.home.FeedSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
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
    val facets: Map<String, String>,
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
    /** 与 isLoggedIn 同一取舍：只依赖能力接口而非仓库，外壳保持无状态依赖，单测也好塞 */
    private val favorites: ShellFavorites,
    private val settingsRepository: SettingsRepository,
    /** 按源问登录态：poipiku 与 pixiv 是两套账号体系，互不放行 */
    private val isLoggedIn: SourceLogin,
    private val config: SourceFeedConfig,
) : ViewModel() {

    data class UiState(
        val source: WorkSource = WorkSource.POIPIKU,
        /** 当前源的声明，UI 据此渲染 tab 行与维度选择 */
        val feeds: List<SourceFeed> = emptyList(),
        val facets: List<SourceFacetGroup> = emptyList(),
        val feedId: String = "",
        /** 各维度组的当前选项：组 id -> 选项 id */
        val facetChoices: Map<String, String> = emptyMap(),
        /** 当前流按名次排列（榜单）：外壳给前三名 hero 位、其余挂名次角标 */
        val ranked: Boolean = false,
        /** 当前流是占位流：能力未到，外壳展示"即将上线"而不是空态 */
        val comingSoon: Boolean = false,
        val items: List<Work> = emptyList(),
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val endReached: Boolean = false,
        val failed: Boolean = false,
        val loadMoreFailed: Boolean = false,
        val needLogin: Boolean = false,
        /** 下拉刷新的「新增 N 条」提示：null 不显示；无时间序的流 loader 自行不算 */
        val refreshNotice: Int? = null,
        /** 登录门文案（声明带入），门屏点明是哪个源的账号 */
        val loginPromptRes: Int = R.string.home_follow_login,
        /** 收藏状态（键带源）：卡片心形与详情都从这里取 */
        val favoriteIds: Set<WorkKey> = emptySet(),
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
        viewModelScope.launch {
            favorites.favoriteIds.collect { ids ->
                _ui.update { it.copy(favoriteIds = ids) }
            }
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
        selections[state.source] = Selection(feedId, state.facetChoices)
        switchLoader()
    }

    fun selectFacet(groupId: String, optionId: String) {
        val state = _ui.value
        if (state.facetChoices[groupId] == optionId) return
        selections[state.source] = Selection(state.feedId, state.facetChoices + (groupId to optionId))
        switchLoader()
    }

    /** 点开作品的去向，由当前源的声明决定 */
    fun open(work: Work): SourceWorkOpen = sourceRegistry.byId(_ui.value.source).open(work)

    fun toggleFavorite(work: Work) {
        viewModelScope.launch { favorites.toggle(work) }
    }

    fun loadMore() = currentLoader()?.loadMore()

    fun retry() = currentLoader()?.refresh(countNotice = false)

    /** 下拉刷新：与重试不同，时间序流要计算「新增 N 条」提示 */
    fun refresh() = currentLoader()?.refresh(countNotice = true)

    fun dismissRefreshNotice() = currentLoader()?.clearNotice()

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
        val selection = selections[source] ?: defaultSelection(declaration)
        selections[source] = selection
        val key = SourceKey(source, selection.feedId, selection.facets)
        currentKey = key
        // 占位流（能力未到）：不建加载器、不发请求，UI 展示"即将上线"
        if (declaration.feed(key.feedId).comingSoon) {
            currentCollectJob?.cancel()
            currentCollectJob = null
            applySnapshot(key, FeedSnapshot<Work>(), declaration)
            return
        }
        val loader = obtainLoader(key, declaration)
        applySnapshot(key, loader.state.value, declaration)
        currentCollectJob?.cancel()
        currentCollectJob = viewModelScope.launch {
            // 守卫：切换后到达的旧快照不得覆盖新流的状态
            loader.state.collect { if (currentKey == key) applySnapshot(key, it, declaration) }
        }
    }

    /**
     * 默认选择：流取声明序第一个"当前可用"的——占位流、未登录时的登录门流都跳过，
     * 例如 pixiv 未登录默认落在榜单而不是占位的推荐；每组维度认 selectedByDefault，
     * 都没有就取该组第一个（有维度必有选中，UI 显示与取参才不会差一档）。
     */
    private fun defaultSelection(declaration: ContentSource): Selection = Selection(
        feedId = declaration.feeds.firstOrNull {
            !it.comingSoon && (!it.requiresLogin || isLoggedIn(declaration.id))
        }?.id ?: declaration.feeds.first().id,
        facets = declaration.facets.associate { group ->
            group.id to (group.options.firstOrNull { it.selectedByDefault }?.id
                ?: group.options.first().id)
        },
    )

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
                    declaration.page(key.feedId, key.facets, page)
                        .onSuccess { totalPages = it.totalPages }
                        .map { it.items }
                }
            },
            isLoggedIn = { isLoggedIn(key.source) },
            idOf = { it.id },
            prefetchEnabled = config.prefetchEnabled,
        )
        loader.refresh(countNotice = false)
        return loader
    }

    private fun currentLoader(): FeedLoader<SourceKey, Work>? {
        val state = _ui.value
        val key = SourceKey(state.source, state.feedId, state.facetChoices)
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
                facetChoices = key.facets,
                ranked = declaration.feed(key.feedId).ranked,
                comingSoon = declaration.feed(key.feedId).comingSoon,
                items = snap.items,
                loading = snap.loading,
                loadingMore = snap.loadingMore,
                endReached = snap.endReached,
                failed = snap.error != null,
                loadMoreFailed = snap.loadMoreError != null,
                needLogin = snap.needLogin,
                refreshNotice = snap.refreshNotice,
                loginPromptRes = declaration.loginPromptRes,
            )
        }
    }

    private fun invalidateAll() {
        loaders.values.toList().forEach { it.dispose() }
        loaders.clear()
        switchLoader()
    }

    private data class Selection(val feedId: String, val facets: Map<String, String>)

    private companion object {
        const val MAX_LOADERS = 12
    }
}
