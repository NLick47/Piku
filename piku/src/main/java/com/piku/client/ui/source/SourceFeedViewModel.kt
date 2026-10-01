package com.piku.client.ui.source

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.domain.model.WorkKey
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.ContentSource
import com.piku.client.domain.source.SourceFacetGroup
import com.piku.client.domain.source.SourceFeed
import com.piku.client.domain.source.sanitizeFacetChoices
import com.piku.client.domain.model.Work
import com.piku.client.domain.source.ShellFavorites
import com.piku.client.domain.source.SourceAuthRegistry
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
    /** 按源问登录态：poipiku 与 pixiv 是两套账号体系，互不放行 */
    private val sourceAuth: SourceAuthRegistry,
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
        /** 当前流带原作宽高：卡片按原图比例排版，不裁方 */
        val proportional: Boolean = false,
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
        /** 当前源的登录页路由；null = 该源没有应用内登录页，门屏不显示「去登录」 */
        val loginRoute: String? = null,
        /** 收藏状态（键带源）：卡片心形与详情都从这里取 */
        val favoriteIds: Set<WorkKey> = emptySet(),
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var currentKey: SourceKey? = null
    private var currentCollectJob: Job? = null

    /** 当前源的登录态订阅：登录/登出后缓存里的页（含 needLogin 标记）全脏了 */
    private var authJob: Job? = null

    /** 每个源记住当前流 */
    private val feedBySource = mutableMapOf<WorkSource, String>()

    /** 每个流记住自己的维度选择：换流不带走别流的筛选（同名维度组在多个流里都存在） */
    private val facetsByFeed = mutableMapOf<FeedKey, Map<String, String>>()

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
    }

    fun selectFeed(feedId: String) {
        val state = _ui.value
        if (feedId == state.feedId) return
        // 只换流，不搬维度：新流按自己的默认维度起，切回来又恢复它自己那套
        feedBySource[state.source] = feedId
        switchLoader()
    }

    fun selectFacet(groupId: String, optionId: String) {
        val state = _ui.value
        if (state.facetChoices[groupId] == optionId) return
        facetsByFeed[FeedKey(state.source, state.feedId)] = state.facetChoices + (groupId to optionId)
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
            authJob?.cancel()
            authJob = null
            _ui.update { it.copy(source = source) }
            return
        }
        _ui.update { it.copy(source = source) }
        observeSourceAuth(source)
        switchLoader()
    }

    /**
     * 跟住当前源的登录态：登录页回来、令牌失效登出都要让门屏/内容立刻换一态。
     * 只订阅当前源——切源时旧的订阅取消，别的源登录了不影响这一页。
     */
    private fun observeSourceAuth(source: WorkSource) {
        authJob?.cancel()
        authJob = null
        val status = sourceAuth.byId(source)?.status ?: return
        authJob = viewModelScope.launch {
            // StateFlow 订阅瞬间会补发当前值，那一发不是变化
            status.drop(1).collect { invalidateAll() }
        }
    }

    private fun switchLoader() {
        val source = _ui.value.source
        if (source == WorkSource.POIPIKU) return
        val declaration = sourceRegistry.byId(source)
        val feedId = feedBySource[source] ?: defaultFeedId(declaration).also { feedBySource[source] = it }
        val facets = declaration.sanitizeFacetChoices(feedId, facetsByFeed[FeedKey(source, feedId)])
        facetsByFeed[FeedKey(source, feedId)] = facets
        val key = SourceKey(source, feedId, facets)
        currentKey = key
        // 占位流（能力未到，或"登录后才接得上"且已登录）：不建加载器、不发请求，
        // UI 展示"即将上线"；未登录时它仍是登录门，由 loader 判定
        if (isComingSoon(declaration, key.feedId)) {
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
     * 默认流：取声明序第一个"当前可用"的——占位流、未登录时的登录门流都跳过，
     * "登录后才接得上"的流登录后也没有数据，一并跳过（pixiv 未登录默认落在榜单而不是占位的推荐）。
     */
    private fun defaultFeedId(declaration: ContentSource): String = declaration.feeds.firstOrNull {
        !it.comingSoon &&
            !(it.pendingAfterLogin && sourceAuth.isLoggedIn(declaration.id)) &&
            (!it.requiresLogin || sourceAuth.isLoggedIn(declaration.id))
    }?.id ?: declaration.feeds.first().id


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
            isLoggedIn = { sourceAuth.isLoggedIn(key.source) },
            idOf = { it.id },
            prefetchEnabled = config.prefetchEnabled,
        )
        loader.refresh(countNotice = false)
        return loader
    }

    /**
     * 该流此刻是不是"占位"：声明就占位的，或"登录后才接得上"且已经登录——
     * 后者登录前是登录门，登录后没有数据可给，只能明说即将上线，而不是发一个注定失败的请求。
     */
    private fun isComingSoon(declaration: ContentSource, feedId: String): Boolean {
        val feed = declaration.feed(feedId)
        return feed.comingSoon || (feed.pendingAfterLogin && sourceAuth.isLoggedIn(declaration.id))
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
                proportional = declaration.feed(key.feedId).proportional,
                comingSoon = isComingSoon(declaration, key.feedId),
                items = snap.items,
                loading = snap.loading,
                loadingMore = snap.loadingMore,
                endReached = snap.endReached,
                failed = snap.error != null,
                loadMoreFailed = snap.loadMoreError != null,
                needLogin = snap.needLogin,
                refreshNotice = snap.refreshNotice,
                loginPromptRes = declaration.loginPromptRes,
                loginRoute = sourceAuth.byId(key.source)?.loginRoute,
            )
        }
    }

    private fun invalidateAll() {
        loaders.values.toList().forEach { it.dispose() }
        loaders.clear()
        switchLoader()
    }

    private data class FeedKey(val source: WorkSource, val feedId: String)

    private companion object {
        const val MAX_LOADERS = 12
    }
}
