package com.piku.client.ui.follow

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.data.repository.AuthRepository
import com.piku.client.data.repository.DetailRepository
import com.piku.client.data.repository.FollowResult
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.AuthStatus
import com.piku.client.domain.model.UserPageInfo
import com.piku.client.domain.model.Work
import com.piku.client.domain.usecase.LoadUserWorksUseCase
import com.piku.client.domain.usecase.ObserveFavoriteIdsUseCase
import com.piku.client.domain.usecase.ToggleFavoriteUseCase
import com.piku.client.ui.common.toFeedErrorRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserWorksUiState(
    val userId: Long = -1L,
    /** 用户昵称：来自来源页（关注列表）传入，或从作品首条解析回填 */
    val userName: String = "",
    /** 用户主页头部信息（页头图/头像/作品数/背景规则），第一页加载后填充 */
    val pageInfo: UserPageInfo? = null,
    val works: List<Work> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val errorRes: Int? = null,
    val loadMoreErrorRes: Int? = null,
    val endReached: Boolean = false,
    /** 当前是否已登录（决定关注按钮显示与可用性） */
    val loggedIn: Boolean = false,
    /** 是否已关注该作者：本页从关注列表进入，默认已关注 */
    val followed: Boolean = true,
    /** 关注操作进行中（防连点） */
    val followSending: Boolean = false,
    /** 关注操作结果反馈（Snackbar 文案资源） */
    val followFeedbackRes: Int? = null,
)

@HiltViewModel
class UserWorksViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loadUserWorksUseCase: LoadUserWorksUseCase,
    private val observeFavoriteIdsUseCase: ObserveFavoriteIdsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val detailRepository: DetailRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val userId: Long = savedStateHandle["userId"] ?: -1L
    private val userNameArg: String = savedStateHandle["userName"] ?: ""

    private val _uiState = MutableStateFlow(UserWorksUiState(userId = userId, userName = userNameArg))
    val uiState: StateFlow<UserWorksUiState> = _uiState.asStateFlow()

    private var page = 0
    private var generation = 0

    init {
        viewModelScope.launch {
            observeFavoriteIdsUseCase().collect { ids ->
                _uiState.update { it.copy(favoriteIds = ids) }
            }
        }
        viewModelScope.launch {
            authRepository.authStatus.collect { status ->
                _uiState.update { it.copy(loggedIn = status == AuthStatus.LOGGED_IN) }
            }
        }
        loadFirstPage()
    }

    fun toggleFavorite(work: Work) {
        viewModelScope.launch { toggleFavoriteUseCase(work) }
    }

    /** 关注/取消关注作者：未登录提示登录，操作中防连点 */
    fun toggleFollow() {
        val state = _uiState.value
        if (state.followSending) return
        if (!state.loggedIn) {
            _uiState.update { it.copy(followFeedbackRes = R.string.detail_follow_login_hint) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(followSending = true) }
            val result = detailRepository.updateFollow(userId)
            _uiState.update { s ->
                s.copy(
                    followSending = false,
                    followFeedbackRes = when (result) {
                        FollowResult.Followed -> R.string.detail_follow_sent
                        FollowResult.Unfollowed -> R.string.detail_unfollow_sent
                        FollowResult.NotLoggedIn -> R.string.detail_follow_login_hint
                        is FollowResult.Failure -> R.string.detail_follow_failed
                    },
                    followed = when (result) {
                        FollowResult.Followed -> true
                        FollowResult.Unfollowed -> false
                        else -> s.followed
                    },
                )
            }
        }
    }

    fun clearFollowFeedback() {
        _uiState.update { it.copy(followFeedbackRes = null) }
    }

    fun retry() {
        generation++
        page = 0
        _uiState.update {
            it.copy(
                works = emptyList(),
                pageInfo = null,
                loading = false,
                loadingMore = false,
                errorRes = null,
                loadMoreErrorRes = null,
                endReached = false,
            )
        }
        loadFirstPage()
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || state.endReached || state.errorRes != null || state.loadMoreErrorRes != null) return
        loadPage(append = true)
    }

    fun retryLoadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || state.endReached || state.errorRes != null || state.loadMoreErrorRes == null) return
        _uiState.update { it.copy(loadMoreErrorRes = null) }
        loadPage(append = true)
    }

    private fun loadFirstPage() {
        if (_uiState.value.loading) return
        loadPage(append = false)
    }

    private fun loadPage(append: Boolean) {
        val gen = generation
        val targetPage = if (append) page + 1 else 0
        _uiState.update {
            if (append) it.copy(loadingMore = true, loadMoreErrorRes = null)
            else it.copy(loading = true, errorRes = null, loadMoreErrorRes = null)
        }
        viewModelScope.launch {
            loadUserWorksUseCase(userId, targetPage)
                .onSuccess { result ->
                    if (generation != gen) return@launch
                    page = targetPage
                    val list = result.works
                    // 昵称为空时从作品首条回填（作品解析自带作者名）
                    val name = _uiState.value.userName
                        .ifBlank { list.firstOrNull()?.authorName.orEmpty() }
                    _uiState.update {
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            loadMoreErrorRes = null,
                            userName = name,
                            // 页头信息只在第一页刷新，避免分页响应覆盖（分页 pageInfo 为 null）
                            pageInfo = result.pageInfo ?: it.pageInfo,
                            works = if (append) it.works + list else list,
                            endReached = list.isEmpty(),
                        )
                    }
                }
                .onFailure { error ->
                    if (generation != gen) return@launch
                    android.util.Log.d(
                        "PikuDiag",
                        "user works load fail userId=$userId append=$append page=$targetPage " +
                            "error=${error::class.simpleName}: ${error.message}",
                        error,
                    )
                    _uiState.update {
                        if (append) {
                            it.copy(
                                loading = false,
                                loadingMore = false,
                                loadMoreErrorRes = (error as? AppError)?.toFeedErrorRes(),
                            )
                        } else {
                            it.copy(
                                loading = false,
                                loadingMore = false,
                                errorRes = (error as? AppError)?.toFeedErrorRes(),
                            )
                        }
                    }
                }
        }
    }
}
