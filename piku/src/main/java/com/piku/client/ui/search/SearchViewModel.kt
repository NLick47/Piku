package com.piku.client.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.repository.AuthRepository
import com.piku.client.data.repository.reloadOnSessionChange
import com.piku.client.data.repository.DetailRepository
import com.piku.client.data.repository.FollowResult
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.model.PopularTag
import com.piku.client.domain.model.TagCard
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkKey
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.SearchFilterGroupSpec
import com.piku.client.domain.source.SearchFilterToggleSpec
import com.piku.client.domain.source.SourceAuthorOpen
import com.piku.client.domain.source.SourceSearch
import com.piku.client.domain.source.SourceSearchRegistry
import com.piku.client.domain.source.SourceAuthRegistry
import com.piku.client.domain.source.SourceSuggestion
import com.piku.client.domain.source.SourceTrendingTag
import com.piku.client.domain.usecase.ClearSearchHistoryUseCase
import com.piku.client.domain.usecase.LoadKeywordFeedUseCase
import com.piku.client.domain.usecase.LoadPopularTagsUseCase
import com.piku.client.domain.usecase.LoadTagFeedUseCase
import com.piku.client.domain.usecase.LoadTagSuggestionsUseCase
import com.piku.client.domain.usecase.LoadUserSearchUseCase
import com.piku.client.domain.usecase.ObserveCustomTagsUseCase
import com.piku.client.domain.usecase.ObserveFavoriteIdsUseCase
import com.piku.client.domain.usecase.ObserveSearchHistoryUseCase
import com.piku.client.domain.usecase.RecordSearchKeywordUseCase
import com.piku.client.domain.usecase.RemoveSearchKeywordUseCase
import com.piku.client.domain.usecase.ToggleFavoriteUseCase
import com.piku.client.domain.usecase.TranslateSearchKeywordUseCase
import com.piku.client.ui.common.toFeedErrorRes
import dagger.hilt.android.lifecycle.HiltViewModel
import com.piku.client.ui.common.FeedbackChannel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchTab { WORKS, USERS, TAGS }

/** 联想防抖与长度上限 */
private const val SUGGEST_DEBOUNCE_MS = 250L
private const val MAX_SUGGEST_LENGTH = 40

/**
 * 统一搜索页状态：
 * - 作品 tab：关键词搜索作品（SearchIllustByKeywordPcV）
 * - 用户 tab：作者搜索（SearchUserByKeywordPcV，需登录）
 * - 标签 tab：标签建议（SearchTagByKeywordPcV，标签卡片）+ 选中标签的作品（SearchIllustByTagPcV）
 */
data class SearchUiState(
    /** 路由传入的原始关键词（可为空串表示未搜索） */
    val keyword: String = "",
    val history: List<String> = emptyList(),
    val popularTagNames: List<String> = emptyList(),
    val tab: SearchTab = SearchTab.WORKS,
    val works: List<Work> = emptyList(),
    val worksLoading: Boolean = false,
    val worksLoadingMore: Boolean = false,
    val worksErrorRes: Int? = null,
    val worksLoadMoreErrorRes: Int? = null,
    val worksEndReached: Boolean = false,
    val worksNeedLogin: Boolean = false,
    val users: List<FollowUser> = emptyList(),
    val usersLoading: Boolean = false,
    val usersLoadingMore: Boolean = false,
    val usersErrorRes: Int? = null,
    val usersLoadMoreErrorRes: Int? = null,
    val usersEndReached: Boolean = false,
    val usersNeedLogin: Boolean = false,
    /** 标签建议（包含关键字的标签卡片，SearchTagByKeywordPcV） */
    val tagSuggestions: List<TagCard> = emptyList(),
    val tagSuggestionsLoading: Boolean = false,
    val tagSuggestionsLoadingMore: Boolean = false,
    val tagSuggestionsErrorRes: Int? = null,
    val tagSuggestionsLoadMoreErrorRes: Int? = null,
    val tagSuggestionsEndReached: Boolean = false,
    /** 选中的精确标签（非空 = 作品模式：展示该标签下的作品）；空 = 建议模式：展示标签卡片 */
    val selectedTagName: String? = null,
    /** 选中标签下的作品（SearchIllustByTagPcV） */
    val tagWorks: List<Work> = emptyList(),
    val tagWorksLoading: Boolean = false,
    val tagWorksLoadingMore: Boolean = false,
    val tagWorksErrorRes: Int? = null,
    val tagWorksLoadMoreErrorRes: Int? = null,
    val tagWorksEndReached: Boolean = false,
    /**
     * 标签建议不可用（接口 SearchTagByKeywordPcV 需登录）。
     * 作品模式不需要登录，所以该标记只在建议模式（[selectedTagName] 为空）下弹登录引导；
     * 非空时 UI 隐藏"返回标签建议"入口。
     */
    val tagNeedLogin: Boolean = false,
    val customTags: List<String> = emptyList(),
    val favoriteIds: Set<WorkKey> = emptySet(),
    /** 正在切换关注状态的用户 ID 集合，防止连点 */
    val followPendingIds: Set<Long> = emptySet(),
    /** 本地乐观覆盖：userId -> 目标关注态，服务端确认后以服务端结果为准 */
    val followOverrides: Map<Long, Boolean> = emptyMap(),
    // ---- 检索插件（pixiv 等）声明的能力区；未声明时全部保持默认，外壳走原链路 ----
    val pluginActive: Boolean = false,
    /** 结果卡片按原图比例排版 */
    val proportional: Boolean = false,
    /** 待机态热门标签墙 */
    val trending: List<SourceTrendingTag> = emptyList(),
    /** 输入联想（标签 + 译名） */
    val suggestions: List<SourceSuggestion> = emptyList(),
    /** 检索筛选声明与当前选中值（组 → 选项 id，开关 → "1"） */
    val filterGroups: List<SearchFilterGroupSpec> = emptyList(),
    val filterToggles: List<SearchFilterToggleSpec> = emptyList(),
    val selectedFilters: Map<String, String> = emptyMap(),
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val sourceSearchRegistry: SourceSearchRegistry,
    private val sourceAuthRegistry: SourceAuthRegistry,
    private val observeSearchHistoryUseCase: ObserveSearchHistoryUseCase,
    private val recordSearchKeywordUseCase: RecordSearchKeywordUseCase,
    private val removeSearchKeywordUseCase: RemoveSearchKeywordUseCase,
    private val clearSearchHistoryUseCase: ClearSearchHistoryUseCase,
    private val translateSearchKeywordUseCase: TranslateSearchKeywordUseCase,
    private val loadPopularTagsUseCase: LoadPopularTagsUseCase,
    private val loadKeywordFeedUseCase: LoadKeywordFeedUseCase,
    private val loadTagSuggestionsUseCase: LoadTagSuggestionsUseCase,
    private val loadTagFeedUseCase: LoadTagFeedUseCase,
    private val loadUserSearchUseCase: LoadUserSearchUseCase,
    private val observeFavoriteIdsUseCase: ObserveFavoriteIdsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val observeCustomTagsUseCase: ObserveCustomTagsUseCase,
    private val detailRepository: DetailRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val keyword: String = savedStateHandle["keyword"] ?: ""

    /**
     * 去除 #/@ 前缀并去首尾空白后的真实搜索词；
     * # 可叠加（站点作者分类标签写作 "##東方"），一并去掉；
     * 空串表示未搜索（历史 + 热门标签的待机态）。
     */
    private val base: String =
        keyword.trimStart('#').removePrefix("@").trim()

    /**
     * 详情页点标签进来时带的精确标签名（空串 = 从搜索框进来）。
     * 非空则落地即该标签的作品列表，跳过"标签建议 → 点卡片"这一步——
     * 用户点的是确定的标签，再让他从建议里挑一次是多余的。
     */
    private val presetTag: String = (savedStateHandle["tag"] ?: "").trim()

    /** 初始 tab：带精确标签 / # 前缀直达标签 tab，@ 前缀直达用户 tab，普通词停在作品 tab */
    private val initialTab: SearchTab = when {
        presetTag.isNotEmpty() -> SearchTab.TAGS
        keyword.startsWith("#") && base.isNotEmpty() -> SearchTab.TAGS
        keyword.startsWith("@") && base.isNotEmpty() -> SearchTab.USERS
        else -> SearchTab.WORKS
    }

    // 当前首页源与其检索插件；搜索页内不换源，进入时锁定即可
    private val source: WorkSource = settingsRepository.homeSource.value
    private val searchPlugin: SourceSearch? = sourceSearchRegistry.byIdOrNull(source)

    /** 各筛选组的默认选中项；开关默认不选 */
    private fun defaultFilters(plugin: SourceSearch): Map<String, String> = buildMap {
        plugin.filterGroups.forEach { group ->
            group.options.firstOrNull { it.default }?.let { put(group.id, it.id) }
        }
    }

    private fun isLoggedInForSearch(): Boolean = when (searchPlugin) {
        null -> authRepository.isLoggedIn()
        else -> sourceAuthRegistry.isLoggedIn(source)
    }

    private val _uiState = MutableStateFlow(
        SearchUiState(
            keyword = keyword,
            tab = initialTab,
            pluginActive = searchPlugin != null,
            proportional = searchPlugin?.proportional == true,
            filterGroups = searchPlugin?.filterGroups ?: emptyList(),
            filterToggles = searchPlugin?.filterToggles ?: emptyList(),
            selectedFilters = searchPlugin?.let(::defaultFilters) ?: emptyMap(),
        ),
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** 一次性反馈（关注操作结果） */
    val feedback = FeedbackChannel()

    private var worksPage = 0
    private var usersPage = 0
    private var tagSuggestionsPage = 0
    private var tagWorksPage = 0
    private var suggestJob: Job? = null

    init {
        viewModelScope.launch {
            observeSearchHistoryUseCase().collect { keywords ->
                _uiState.update { it.copy(history = keywords) }
            }
        }
        viewModelScope.launch {
            observeFavoriteIdsUseCase().collect { ids ->
                _uiState.update { it.copy(favoriteIds = ids) }
            }
        }
        viewModelScope.launch {
            observeCustomTagsUseCase().collect { tags ->
                _uiState.update { it.copy(customTags = tags) }
            }
        }
        if (searchPlugin == null) {
            // poipiku 的热门标签；声明了检索插件的源有自己的热门区，不混用
            viewModelScope.launch {
                loadPopularTagsUseCase().onSuccess { tags ->
                    val names = tags.map(PopularTag::name)
                    _uiState.update { it.copy(popularTagNames = names) }
                }
            }
        } else {
            viewModelScope.launch {
                searchPlugin.trendingTags().onSuccess { list ->
                    _uiState.update { it.copy(trending = list) }
                }
            }
        }
        // 登录成功（含从登录引导回来）、自动重登、登出都要重载三个 tab：
        // 搜索结果与身份相关，登出后不能继续显示上一个账号才看得见的内容
        viewModelScope.reloadOnSessionChange(authRepository.sessionVersion) {
            usersPage = 0
            loadUsers(append = false)
            loadWorks(append = false)
            loadTagsByMode(append = false)
        }
        if (base.isNotEmpty() || presetTag.isNotEmpty()) {
            // 作品 tab 始终预载（关键词搜索，切 tab 免等待）
            loadWorks(append = false)
            when {
                // 详情页点标签进来：直达该标签的作品列表，跳过标签建议
                // （匿名时"返回标签建议"入口由 loadTagWorks 按登录态隐藏）
                presetTag.isNotEmpty() -> selectTagCard(presetTag)
                initialTab == SearchTab.TAGS -> loadTagSuggestions(append = false)
                // 用户 tab：无条件调用，内部处理未登录态（usersNeedLogin）
                initialTab == SearchTab.USERS -> loadUsers(append = false)
                // 已登录用户直接预载作者列表，切到用户 tab 时无需等待
                else -> if (isLoggedInForSearch()) loadUsers(append = false)
            }
        }
    }

    fun record(keyword: String) {
        viewModelScope.launch { recordSearchKeywordUseCase(keyword) }
    }

    /** 一键译搜：纯汉字→日语、含假名→中文（保留 #/@ 前缀）；null = 翻译失败 */
    suspend fun translateKeyword(keyword: String, toJapanese: Boolean): String? =
        translateSearchKeywordUseCase(keyword, toJapanese)

    fun removeHistory(keyword: String) {
        viewModelScope.launch { removeSearchKeywordUseCase(keyword) }
    }

    fun clearHistory() {
        viewModelScope.launch { clearSearchHistoryUseCase() }
    }

    fun selectTab(tab: SearchTab) {
        val state = _uiState.value
        if (tab == state.tab || base.isEmpty()) return
        _uiState.update { it.copy(tab = tab) }
        when (tab) {
            SearchTab.WORKS -> if (state.works.isEmpty() && !state.worksLoading && !state.worksEndReached) loadWorks(append = false)
            SearchTab.USERS -> if (state.users.isEmpty() && !state.usersLoading && !state.usersEndReached) loadUsers(append = false)
            SearchTab.TAGS -> {
                val selected = state.selectedTagName != null
                val empty = if (selected) state.tagWorks.isEmpty() else state.tagSuggestions.isEmpty()
                val loading = if (selected) state.tagWorksLoading else state.tagSuggestionsLoading
                val endReached = if (selected) state.tagWorksEndReached else state.tagSuggestionsEndReached
                if (empty && !loading && !endReached) loadTagsByMode(append = false)
            }
        }
    }

    fun retryWorks() = loadWorks(append = false)

    fun retryUsers() = loadUsers(append = false)

    fun retryTags() {
        if (_uiState.value.selectedTagName != null) loadTagWorks(append = false)
        else loadTagSuggestions(append = false)
    }

    fun loadMoreWorks() {
        val state = _uiState.value
        if (base.isEmpty() || state.worksLoading || state.worksLoadingMore ||
            state.worksEndReached || state.worksErrorRes != null || state.worksLoadMoreErrorRes != null
        ) return
        loadWorks(append = true)
    }

    fun retryLoadMoreWorks() {
        val state = _uiState.value
        if (state.worksLoadMoreErrorRes == null) return
        _uiState.update { it.copy(worksLoadMoreErrorRes = null) }
        loadWorks(append = true)
    }

    fun loadMoreUsers() {
        val state = _uiState.value
        if (base.isEmpty() || state.usersLoading || state.usersLoadingMore ||
            state.usersEndReached || state.usersErrorRes != null || state.usersLoadMoreErrorRes != null
        ) return
        loadUsers(append = true)
    }

    fun retryLoadMoreUsers() {
        val state = _uiState.value
        if (state.usersLoadMoreErrorRes == null) return
        _uiState.update { it.copy(usersLoadMoreErrorRes = null) }
        loadUsers(append = true)
    }

    fun loadMoreTags() {
        val state = _uiState.value
        val selected = state.selectedTagName != null
        val loading = if (selected) state.tagWorksLoading || state.tagWorksLoadingMore
        else state.tagSuggestionsLoading || state.tagSuggestionsLoadingMore
        val endReached = if (selected) state.tagWorksEndReached else state.tagSuggestionsEndReached
        val hasError = if (selected) {
            state.tagWorksErrorRes != null || state.tagWorksLoadMoreErrorRes != null
        } else {
            state.tagSuggestionsErrorRes != null || state.tagSuggestionsLoadMoreErrorRes != null
        }
        if (base.isEmpty() || loading || endReached || hasError) return
        if (selected) loadTagWorks(append = true) else loadTagSuggestions(append = true)
    }

    fun retryLoadMoreTags() {
        val state = _uiState.value
        val selected = state.selectedTagName != null
        val loadMoreError = if (selected) state.tagWorksLoadMoreErrorRes else state.tagSuggestionsLoadMoreErrorRes
        if (loadMoreError == null) return
        _uiState.update {
            if (selected) it.copy(tagWorksLoadMoreErrorRes = null)
            else it.copy(tagSuggestionsLoadMoreErrorRes = null)
        }
        if (selected) loadTagWorks(append = true) else loadTagSuggestions(append = true)
    }

    fun selectTagCard(name: String) {
        if (name.isEmpty() || _uiState.value.selectedTagName == name) return
        tagWorksPage = 0
        _uiState.update {
            it.copy(
                selectedTagName = name,
                tagWorks = emptyList(),
                tagWorksLoading = false,
                tagWorksLoadingMore = false,
                tagWorksErrorRes = null,
                tagWorksLoadMoreErrorRes = null,
                tagWorksEndReached = false,
            )
        }
        // tagNeedLogin 由 loadTagWorks 按登录态刷新，这里不碰
        loadTagWorks(append = false)
    }

    /**
     * 返回标签建议模式。
     * 直达标签作品列表进来时建议还没请求过（[presetTag] 路径），此处补载一次，
     * 否则会停在"未找到相关标签"的空态且不会自己加载。
     */
    fun backToTagSuggestions() {
        if (_uiState.value.selectedTagName == null) return
        _uiState.update {
            it.copy(
                selectedTagName = null,
                tagWorks = emptyList(),
                tagWorksLoading = false,
                tagWorksLoadingMore = false,
                tagWorksErrorRes = null,
                tagWorksLoadMoreErrorRes = null,
                tagWorksEndReached = false,
            )
        }
        val state = _uiState.value
        if (state.tagSuggestions.isEmpty() && !state.tagSuggestionsLoading && !state.tagSuggestionsEndReached) {
            loadTagSuggestions(append = false)
        }
    }

    fun toggleFavorite(work: Work) {
        viewModelScope.launch { toggleFavoriteUseCase(work) }
    }

    /** 输入联想：防抖拉标签建议；空串/超长/未登录不发，链接输入不联想 */
    fun onQueryInput(text: String) {
        suggestJob?.cancel()
        val plugin = searchPlugin ?: return
        val q = text.trim()
        if (q.isEmpty() || q.length > MAX_SUGGEST_LENGTH || !isLoggedInForSearch()) {
            clearSuggestions()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            plugin.suggest(q)
                .onSuccess { list -> _uiState.update { it.copy(suggestions = list) } }
                .onFailure { _uiState.update { it.copy(suggestions = emptyList()) } }
        }
    }

    fun clearSuggestions() {
        suggestJob?.cancel()
        if (_uiState.value.suggestions.isNotEmpty()) {
            _uiState.update { it.copy(suggestions = emptyList()) }
        }
    }

    /** 应用筛选：记下选中值，作品与选中的标签作品全量重载 */
    fun applyFilters(selected: Map<String, String>) {
        if (searchPlugin == null) return
        _uiState.update { it.copy(selectedFilters = selected) }
        if (base.isEmpty()) return
        worksPage = 0
        loadWorks(append = false)
        if (_uiState.value.selectedTagName != null) {
            tagWorksPage = 0
            loadTagWorks(append = false)
        }
    }

    /** 点用户的去向：插件声明了就照声明（pixiv 出站到 pixiv 用户页），null 走默认用户页 */
    fun userOpen(user: FollowUser): SourceAuthorOpen? = searchPlugin?.userPage(user)

    fun toggleFollow(userId: Long) {
        val plugin = searchPlugin
        if (plugin != null) {
            toggleFollowPlugin(plugin, userId)
            return
        }
        val state = _uiState.value
        if (userId in state.followPendingIds) return
        val current = state.followOverrides[userId]
            ?: (state.users.find { it.userId == userId }?.followed ?: false)
        val target = !current
        _uiState.update {
            it.copy(
                followPendingIds = it.followPendingIds + userId,
                followOverrides = it.followOverrides + (userId to target),
            )
        }
        viewModelScope.launch {
            val result = detailRepository.updateFollow(userId)
            _uiState.update { s ->
                val stillPending = s.followPendingIds - userId
                when (result) {
                    is FollowResult.Followed -> s.copy(
                        followPendingIds = stillPending,
                        followOverrides = s.followOverrides + (userId to true),
                    )
                    is FollowResult.Unfollowed -> s.copy(
                        followPendingIds = stillPending,
                        followOverrides = s.followOverrides + (userId to false),
                    )
                    is FollowResult.NotLoggedIn -> s.copy(
                        followPendingIds = stillPending,
                        followOverrides = s.followOverrides - userId,
                    )
                    is FollowResult.Failure -> s.copy(
                        followPendingIds = stillPending,
                        followOverrides = s.followOverrides - userId,
                    )
                }
            }
            feedback.show(
                when (result) {
                    is FollowResult.Followed -> R.string.detail_follow_sent
                    is FollowResult.Unfollowed -> R.string.detail_unfollow_sent
                    is FollowResult.NotLoggedIn -> R.string.detail_follow_login_hint
                    is FollowResult.Failure -> R.string.detail_follow_failed
                },
            )
        }
    }

    /** 插件源的关注切换：与 poipiku 路径同一套乐观覆盖与防连点 */
    private fun toggleFollowPlugin(plugin: SourceSearch, userId: Long) {
        val state = _uiState.value
        if (userId in state.followPendingIds) return
        val current = state.followOverrides[userId]
            ?: (state.users.find { it.userId == userId }?.followed ?: false)
        val target = !current
        _uiState.update {
            it.copy(
                followPendingIds = it.followPendingIds + userId,
                followOverrides = it.followOverrides + (userId to target),
            )
        }
        viewModelScope.launch {
            val result = plugin.toggleFollow(userId, target)
            _uiState.update { s ->
                val stillPending = s.followPendingIds - userId
                if (result.isSuccess) {
                    s.copy(
                        followPendingIds = stillPending,
                        followOverrides = s.followOverrides + (userId to target),
                    )
                } else {
                    s.copy(
                        followPendingIds = stillPending,
                        followOverrides = s.followOverrides - userId,
                    )
                }
            }
            feedback.show(
                when {
                    result.isSuccess && target -> R.string.detail_follow_sent
                    result.isSuccess -> R.string.detail_unfollow_sent
                    else -> R.string.detail_follow_failed
                },
            )
        }
    }

    // 站点作者搜索会返回重复条目，而列表以 userId 作 key，重复 key 会让 LazyColumn 直接崩
    private fun mergeUsers(current: List<FollowUser>, incoming: List<FollowUser>): List<FollowUser> {
        val seen = current.mapTo(mutableSetOf()) { it.userId }
        return current + incoming.filter { seen.add(it.userId) }
    }

    private fun loadWorks(append: Boolean) {
        if (base.isEmpty()) return
        val plugin = searchPlugin
        if (plugin != null) {
            loadWorksViaPlugin(plugin, append)
            return
        }
        val targetPage = if (append) worksPage + 1 else 0
        if (!authRepository.isLoggedIn()) {
            _uiState.update {
                it.copy(
                    worksLoading = false,
                    worksLoadingMore = false,
                    worksErrorRes = null,
                    worksLoadMoreErrorRes = null,
                    worksEndReached = true,
                    worksNeedLogin = true,
                    works = if (append) it.works else emptyList(),
                )
            }
            return
        }
        _uiState.update {
            if (append) it.copy(worksLoadingMore = true, worksLoadMoreErrorRes = null)
            else it.copy(worksLoading = true, worksErrorRes = null, worksLoadMoreErrorRes = null, worksNeedLogin = false)
        }
        viewModelScope.launch {
            loadKeywordFeedUseCase(base, targetPage)
                .onSuccess { list ->
                    worksPage = targetPage
                    _uiState.update {
                        it.copy(
                            worksLoading = false,
                            worksLoadingMore = false,
                            worksLoadMoreErrorRes = null,
                            works = if (append) it.works + list else list,
                            worksEndReached = list.isEmpty(),
                        )
                    }
                }
                .onFailure { error ->
                    android.util.Log.d(
                        "PikuDiag",
                        "search works load fail keyword=$base append=$append page=$targetPage " +
                            "error=${error::class.simpleName}: ${error.message}",
                        error,
                    )
                    _uiState.update {
                        if (append) {
                            it.copy(
                                worksLoading = false,
                                worksLoadingMore = false,
                                worksLoadMoreErrorRes = (error as? AppError)?.toFeedErrorRes(),
                            )
                        } else {
                            it.copy(
                                worksLoading = false,
                                worksLoadingMore = false,
                                worksErrorRes = (error as? AppError)?.toFeedErrorRes(),
                            )
                        }
                    }
                }
        }
    }

    private fun loadWorksViaPlugin(plugin: SourceSearch, append: Boolean) {
        val targetPage = if (append) worksPage + 1 else 0
        if (!isLoggedInForSearch()) {
            _uiState.update {
                it.copy(
                    worksLoading = false,
                    worksLoadingMore = false,
                    worksErrorRes = null,
                    worksLoadMoreErrorRes = null,
                    worksEndReached = true,
                    worksNeedLogin = true,
                    works = if (append) it.works else emptyList(),
                )
            }
            return
        }
        _uiState.update {
            if (append) it.copy(worksLoadingMore = true, worksLoadMoreErrorRes = null)
            else it.copy(worksLoading = true, worksErrorRes = null, worksLoadMoreErrorRes = null, worksNeedLogin = false)
        }
        viewModelScope.launch {
            plugin.searchWorks(base, _uiState.value.selectedFilters, targetPage)
                .onSuccess { page ->
                    worksPage = targetPage
                    _uiState.update {
                        it.copy(
                            worksLoading = false,
                            worksLoadingMore = false,
                            worksLoadMoreErrorRes = null,
                            works = if (append) it.works + page.items else page.items,
                            worksEndReached = page.items.isEmpty(),
                        )
                    }
                }
                .onFailure { error ->
                    android.util.Log.d(
                        "PikuDiag",
                        "pixiv search works fail keyword=$base filters=${_uiState.value.selectedFilters} " +
                            "append=$append page=$targetPage error=${error::class.simpleName}: ${error.message}",
                        error,
                    )
                    _uiState.update {
                        if (append) {
                            it.copy(
                                worksLoading = false,
                                worksLoadingMore = false,
                                worksLoadMoreErrorRes = (error as? AppError)?.toFeedErrorRes(),
                            )
                        } else {
                            it.copy(
                                worksLoading = false,
                                worksLoadingMore = false,
                                worksErrorRes = (error as? AppError)?.toFeedErrorRes(),
                            )
                        }
                    }
                }
        }
    }

    private fun loadUsers(append: Boolean) {
        if (base.isEmpty()) return
        val plugin = searchPlugin
        if (plugin != null) {
            if (plugin.supportsUsers) {
                loadUsersViaPlugin(plugin, append)
            } else {
                _uiState.update {
                    it.copy(usersLoading = false, usersEndReached = true, users = if (append) it.users else emptyList())
                }
            }
            return
        }
        val targetPage = if (append) usersPage + 1 else 0
        if (!authRepository.isLoggedIn()) {
            _uiState.update {
                it.copy(
                    usersLoading = false,
                    usersLoadingMore = false,
                    usersErrorRes = null,
                    usersLoadMoreErrorRes = null,
                    usersEndReached = true,
                    usersNeedLogin = true,
                    users = if (append) it.users else emptyList(),
                )
            }
            return
        }
        _uiState.update {
            if (append) it.copy(usersLoadingMore = true, usersLoadMoreErrorRes = null)
            else it.copy(usersLoading = true, usersErrorRes = null, usersLoadMoreErrorRes = null, usersNeedLogin = false)
        }
        viewModelScope.launch {
            loadUserSearchUseCase(base, targetPage)
                .onSuccess { list ->
                    usersPage = targetPage
                    _uiState.update {
                        val merged = if (append) mergeUsers(it.users, list) else list.distinctBy { u -> u.userId }
                        it.copy(
                            usersLoading = false,
                            usersLoadingMore = false,
                            usersLoadMoreErrorRes = null,
                            users = merged,
                            usersEndReached = list.isEmpty() || (append && merged.size == it.users.size),
                        )
                    }
                }
                .onFailure { error ->
                    android.util.Log.d(
                        "PikuDiag",
                        "search users load fail keyword=$base append=$append page=$targetPage " +
                            "error=${error::class.simpleName}: ${error.message}",
                        error,
                    )
                    _uiState.update {
                        if (append) {
                            it.copy(
                                usersLoading = false,
                                usersLoadingMore = false,
                                usersLoadMoreErrorRes = (error as? AppError)?.toFeedErrorRes(),
                            )
                        } else {
                            it.copy(
                                usersLoading = false,
                                usersLoadingMore = false,
                                usersErrorRes = (error as? AppError)?.toFeedErrorRes(),
                            )
                        }
                    }
                }
        }
    }

    private fun loadUsersViaPlugin(plugin: SourceSearch, append: Boolean) {
        val targetPage = if (append) usersPage + 1 else 0
        if (!isLoggedInForSearch()) {
            _uiState.update {
                it.copy(
                    usersLoading = false,
                    usersLoadingMore = false,
                    usersErrorRes = null,
                    usersLoadMoreErrorRes = null,
                    usersEndReached = true,
                    usersNeedLogin = true,
                    users = if (append) it.users else emptyList(),
                )
            }
            return
        }
        _uiState.update {
            if (append) it.copy(usersLoadingMore = true, usersLoadMoreErrorRes = null)
            else it.copy(usersLoading = true, usersErrorRes = null, usersLoadMoreErrorRes = null, usersNeedLogin = false)
        }
        viewModelScope.launch {
            plugin.searchUsers(base, targetPage)
                .onSuccess { list ->
                    usersPage = targetPage
                    _uiState.update {
                        val merged = if (append) mergeUsers(it.users, list) else list.distinctBy { u -> u.userId }
                        it.copy(
                            usersLoading = false,
                            usersLoadingMore = false,
                            usersLoadMoreErrorRes = null,
                            users = merged,
                            usersEndReached = list.isEmpty() || (append && merged.size == it.users.size),
                        )
                    }
                }
                .onFailure { error ->
                    android.util.Log.d(
                        "PikuDiag",
                        "pixiv search users fail keyword=$base append=$append page=$targetPage " +
                            "error=${error::class.simpleName}: ${error.message}",
                        error,
                    )
                    _uiState.update {
                        if (append) {
                            it.copy(
                                usersLoading = false,
                                usersLoadingMore = false,
                                usersLoadMoreErrorRes = (error as? AppError)?.toFeedErrorRes(),
                            )
                        } else {
                            it.copy(
                                usersLoading = false,
                                usersLoadingMore = false,
                                usersErrorRes = (error as? AppError)?.toFeedErrorRes(),
                            )
                        }
                    }
                }
        }
    }

    private fun loadTagsByMode(append: Boolean) {
        if (_uiState.value.selectedTagName != null) loadTagWorks(append) else loadTagSuggestions(append)
    }

    private fun loadTagSuggestions(append: Boolean) {
        if (base.isEmpty()) return
        val plugin = searchPlugin
        if (plugin != null) {
            loadTagSuggestionsViaPlugin(plugin)
            return
        }
        val targetPage = if (append) tagSuggestionsPage + 1 else 0
        if (!authRepository.isLoggedIn()) {
            _uiState.update {
                it.copy(
                    tagSuggestionsLoading = false,
                    tagSuggestionsLoadingMore = false,
                    tagSuggestionsErrorRes = null,
                    tagSuggestionsLoadMoreErrorRes = null,
                    tagSuggestionsEndReached = true,
                    tagNeedLogin = true,
                    tagSuggestions = if (append) it.tagSuggestions else emptyList(),
                )
            }
            return
        }
        _uiState.update {
            if (append) it.copy(tagSuggestionsLoadingMore = true, tagSuggestionsLoadMoreErrorRes = null)
            else it.copy(
                tagSuggestionsLoading = true,
                tagSuggestionsErrorRes = null,
                tagSuggestionsLoadMoreErrorRes = null,
                tagNeedLogin = false,
            )
        }
        viewModelScope.launch {
            loadTagSuggestionsUseCase(base, targetPage)
                .onSuccess { list ->
                    tagSuggestionsPage = targetPage
                    _uiState.update {
                        it.copy(
                            tagSuggestionsLoading = false,
                            tagSuggestionsLoadingMore = false,
                            tagSuggestionsLoadMoreErrorRes = null,
                            tagSuggestions = if (append) it.tagSuggestions + list else list,
                            tagSuggestionsEndReached = list.isEmpty(),
                        )
                    }
                }
                .onFailure { error ->
                    android.util.Log.d(
                        "PikuDiag",
                        "search tags load fail keyword=$base append=$append page=$targetPage " +
                            "error=${error::class.simpleName}: ${error.message}",
                        error,
                    )
                    _uiState.update {
                        if (append) {
                            it.copy(
                                tagSuggestionsLoading = false,
                                tagSuggestionsLoadingMore = false,
                                tagSuggestionsLoadMoreErrorRes = (error as? AppError)?.toFeedErrorRes(),
                            )
                        } else {
                            it.copy(
                                tagSuggestionsLoading = false,
                                tagSuggestionsLoadingMore = false,
                                tagSuggestionsErrorRes = (error as? AppError)?.toFeedErrorRes(),
                            )
                        }
                    }
                }
        }
    }

    /** 插件源的标签建议：取自联想接口，一次取完即到底（无翻页） */
    private fun loadTagSuggestionsViaPlugin(plugin: SourceSearch) {
        if (!isLoggedInForSearch()) {
            _uiState.update {
                it.copy(
                    tagSuggestionsLoading = false,
                    tagSuggestionsLoadingMore = false,
                    tagSuggestionsErrorRes = null,
                    tagSuggestionsLoadMoreErrorRes = null,
                    tagSuggestionsEndReached = true,
                    tagNeedLogin = true,
                    tagSuggestions = emptyList(),
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                tagSuggestionsLoading = true,
                tagSuggestionsErrorRes = null,
                tagSuggestionsLoadMoreErrorRes = null,
                tagNeedLogin = false,
            )
        }
        viewModelScope.launch {
            plugin.suggest(base)
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(
                            tagSuggestionsLoading = false,
                            tagSuggestions = list.map { s -> TagCard(name = s.name) },
                            tagSuggestionsEndReached = true,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            tagSuggestionsLoading = false,
                            tagSuggestionsErrorRes = (error as? AppError)?.toFeedErrorRes(),
                        )
                    }
                }
        }
    }

    private fun loadTagWorks(append: Boolean) {
        val tag = _uiState.value.selectedTagName ?: return
        val plugin = searchPlugin
        if (plugin != null) {
            loadTagWorksViaPlugin(plugin, tag, append)
            return
        }
        val targetPage = if (append) tagWorksPage + 1 else 0
        // 精确标签的作品列表（SearchIllustByTagPcV）匿名可用——网页端与标签页都一样，
        // 所以这里不设登录门；需要登录的是标签建议（SearchTagByKeywordPcV）。
        // 登录态同时决定 tagNeedLogin（未登录 = 建议不可用，UI 隐藏"返回标签建议"），
        // 所以每次全量加载都按当前登录态刷新它，登录成功后不会残留匿名时的标记
        _uiState.update {
            if (append) it.copy(tagWorksLoadingMore = true, tagWorksLoadMoreErrorRes = null)
            else it.copy(
                tagWorksLoading = true,
                tagWorksErrorRes = null,
                tagWorksLoadMoreErrorRes = null,
                tagNeedLogin = !authRepository.isLoggedIn(),
            )
        }
        viewModelScope.launch {
            loadTagFeedUseCase(tag, targetPage)
                .onSuccess { list ->
                    if (_uiState.value.selectedTagName != tag) return@launch
                    tagWorksPage = targetPage
                    _uiState.update {
                        it.copy(
                            tagWorksLoading = false,
                            tagWorksLoadingMore = false,
                            tagWorksLoadMoreErrorRes = null,
                            tagWorks = if (append) it.tagWorks + list else list,
                            tagWorksEndReached = list.isEmpty(),
                        )
                    }
                }
                .onFailure { error ->
                    if (_uiState.value.selectedTagName != tag) return@launch
                    android.util.Log.d(
                        "PikuDiag",
                        "search tag works load fail tag=$tag append=$append page=$targetPage " +
                            "error=${error::class.simpleName}: ${error.message}",
                        error,
                    )
                    _uiState.update {
                        if (append) {
                            it.copy(
                                tagWorksLoading = false,
                                tagWorksLoadingMore = false,
                                tagWorksLoadMoreErrorRes = (error as? AppError)?.toFeedErrorRes(),
                            )
                        } else {
                            it.copy(
                                tagWorksLoading = false,
                                tagWorksLoadingMore = false,
                                tagWorksErrorRes = (error as? AppError)?.toFeedErrorRes(),
                            )
                        }
                    }
                }
        }
    }

    /** 插件源的标签作品：按完全一致检索该标签，其余筛选照常生效 */
    private fun loadTagWorksViaPlugin(plugin: SourceSearch, tag: String, append: Boolean) {
        val targetPage = if (append) tagWorksPage + 1 else 0
        _uiState.update {
            if (append) it.copy(tagWorksLoadingMore = true, tagWorksLoadMoreErrorRes = null)
            else it.copy(
                tagWorksLoading = true,
                tagWorksErrorRes = null,
                tagWorksLoadMoreErrorRes = null,
                tagNeedLogin = false,
            )
        }
        viewModelScope.launch {
            plugin.searchTagWorks(tag, _uiState.value.selectedFilters, targetPage)
                .onSuccess { page ->
                    if (_uiState.value.selectedTagName != tag) return@launch
                    tagWorksPage = targetPage
                    _uiState.update {
                        it.copy(
                            tagWorksLoading = false,
                            tagWorksLoadingMore = false,
                            tagWorksLoadMoreErrorRes = null,
                            tagWorks = if (append) it.tagWorks + page.items else page.items,
                            tagWorksEndReached = page.items.isEmpty(),
                        )
                    }
                }
                .onFailure { error ->
                    if (_uiState.value.selectedTagName != tag) return@launch
                    android.util.Log.d(
                        "PikuDiag",
                        "pixiv search tag works fail tag=$tag append=$append page=$targetPage " +
                            "error=${error::class.simpleName}: ${error.message}",
                        error,
                    )
                    _uiState.update {
                        if (append) {
                            it.copy(
                                tagWorksLoading = false,
                                tagWorksLoadingMore = false,
                                tagWorksLoadMoreErrorRes = (error as? AppError)?.toFeedErrorRes(),
                            )
                        } else {
                            it.copy(
                                tagWorksLoading = false,
                                tagWorksLoadingMore = false,
                                tagWorksErrorRes = (error as? AppError)?.toFeedErrorRes(),
                            )
                        }
                    }
                }
        }
    }
}
