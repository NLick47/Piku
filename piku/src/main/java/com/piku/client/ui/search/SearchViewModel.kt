package com.piku.client.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.data.repository.AuthRepository
import com.piku.client.data.repository.DetailRepository
import com.piku.client.data.repository.FollowResult
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.AuthStatus
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.model.PopularTag
import com.piku.client.domain.model.TagCard
import com.piku.client.domain.model.Work
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
import com.piku.client.ui.common.toFeedErrorRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchTab { WORKS, USERS, TAGS }

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
    val tagNeedLogin: Boolean = false,
    val customTags: List<String> = emptyList(),
    val favoriteIds: Set<Long> = emptySet(),
    /** 正在切换关注状态的用户 ID 集合，防止连点 */
    val followPendingIds: Set<Long> = emptySet(),
    /** 本地乐观覆盖：userId -> 目标关注态，服务端确认后以服务端结果为准 */
    val followOverrides: Map<Long, Boolean> = emptyMap(),
    val actionFeedbackRes: Int? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeSearchHistoryUseCase: ObserveSearchHistoryUseCase,
    private val recordSearchKeywordUseCase: RecordSearchKeywordUseCase,
    private val removeSearchKeywordUseCase: RemoveSearchKeywordUseCase,
    private val clearSearchHistoryUseCase: ClearSearchHistoryUseCase,
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
     * 空串表示未搜索（历史 + 热门标签的待机态）。
     */
    private val base: String =
        keyword.removePrefix("#").removePrefix("@").trim()

    /** 初始 tab：按前缀直达对应 tab，普通词停在作品 tab */
    private val initialTab: SearchTab = when {
        keyword.startsWith("#") && base.isNotEmpty() -> SearchTab.TAGS
        keyword.startsWith("@") && base.isNotEmpty() -> SearchTab.USERS
        else -> SearchTab.WORKS
    }

    private val _uiState = MutableStateFlow(
        SearchUiState(keyword = keyword, tab = initialTab),
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var worksPage = 0
    private var usersPage = 0
    private var tagSuggestionsPage = 0
    private var tagWorksPage = 0

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
        viewModelScope.launch {
            loadPopularTagsUseCase().onSuccess { tags ->
                val names = tags.map(PopularTag::name)
                _uiState.update { it.copy(popularTagNames = names) }
            }
        }
        viewModelScope.launch {
            var prevLoggedIn: Boolean? = null
            authRepository.authStatus.collect { status ->
                val loggedIn = status == AuthStatus.LOGGED_IN
                // 登录成功（含从登录引导回来）后重载三个 tab 的搜索
                if (prevLoggedIn == false && loggedIn) {
                    usersPage = 0
                    loadUsers(append = false)
                    loadWorks(append = false)
                    loadTagsByMode(append = false)
                }
                prevLoggedIn = loggedIn
            }
        }
        viewModelScope.launch {
            authRepository.sessionRefreshed.collect {
                loadUsers(append = false)
                loadWorks(append = false)
                loadTagsByMode(append = false)
            }
        }
        if (base.isNotEmpty()) {
            // 作品 tab 始终预载（关键词搜索，切 tab 免等待）
            loadWorks(append = false)
            when (initialTab) {
                SearchTab.TAGS -> loadTagSuggestions(append = false)
                // 用户 tab：无条件调用，内部处理未登录态（usersNeedLogin）
                SearchTab.USERS -> loadUsers(append = false)
                // 已登录用户直接预载作者列表，切到用户 tab 时无需等待
                SearchTab.WORKS -> if (authRepository.isLoggedIn()) loadUsers(append = false)
            }
        }
    }

    fun record(keyword: String) {
        viewModelScope.launch { recordSearchKeywordUseCase(keyword) }
    }

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
                tagNeedLogin = false,
            )
        }
        loadTagWorks(append = false)
    }

    /** 返回标签建议模式 */
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
    }

    fun toggleFavorite(work: Work) {
        viewModelScope.launch { toggleFavoriteUseCase(work) }
    }

    fun toggleFollow(userId: Long) {
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
                        actionFeedbackRes = R.string.detail_follow_sent,
                    )
                    is FollowResult.Unfollowed -> s.copy(
                        followPendingIds = stillPending,
                        followOverrides = s.followOverrides + (userId to false),
                        actionFeedbackRes = R.string.detail_unfollow_sent,
                    )
                    is FollowResult.NotLoggedIn -> s.copy(
                        followPendingIds = stillPending,
                        followOverrides = s.followOverrides - userId,
                        actionFeedbackRes = R.string.detail_follow_login_hint,
                    )
                    is FollowResult.Failure -> s.copy(
                        followPendingIds = stillPending,
                        followOverrides = s.followOverrides - userId,
                        actionFeedbackRes = R.string.detail_follow_failed,
                    )
                }
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(actionFeedbackRes = null) }
    }

    private fun loadWorks(append: Boolean) {
        if (base.isEmpty()) return
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

    private fun loadUsers(append: Boolean) {
        if (base.isEmpty()) return
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
                        it.copy(
                            usersLoading = false,
                            usersLoadingMore = false,
                            usersLoadMoreErrorRes = null,
                            users = if (append) it.users + list else list,
                            usersEndReached = list.isEmpty(),
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

    private fun loadTagsByMode(append: Boolean) {
        if (_uiState.value.selectedTagName != null) loadTagWorks(append) else loadTagSuggestions(append)
    }

    private fun loadTagSuggestions(append: Boolean) {
        if (base.isEmpty()) return
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

    private fun loadTagWorks(append: Boolean) {
        val tag = _uiState.value.selectedTagName ?: return
        val targetPage = if (append) tagWorksPage + 1 else 0
        if (!authRepository.isLoggedIn()) {
            _uiState.update {
                it.copy(
                    tagWorksLoading = false,
                    tagWorksLoadingMore = false,
                    tagWorksErrorRes = null,
                    tagWorksLoadMoreErrorRes = null,
                    tagWorksEndReached = true,
                    tagNeedLogin = true,
                    tagWorks = if (append) it.tagWorks else emptyList(),
                )
            }
            return
        }
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
}
