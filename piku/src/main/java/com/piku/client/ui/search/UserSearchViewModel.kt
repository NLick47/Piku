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
import com.piku.client.domain.usecase.LoadUserSearchUseCase
import com.piku.client.ui.common.toFeedErrorRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserSearchUiState(
    val keyword: String = "",
    val users: List<FollowUser> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val errorRes: Int? = null,
    val loadMoreErrorRes: Int? = null,
    val endReached: Boolean = false,
    /** 作者搜索需登录：未登录不发请求，直接展示登录引导 */
    val searchNeedLogin: Boolean = false,
    /** 正在切换关注状态的用户 ID 集合，防止连点 */
    val followPendingIds: Set<Long> = emptySet(),
    /** 本地乐观覆盖：userId -> 目标关注态，服务端确认后以服务端结果为准 */
    val followOverrides: Map<Long, Boolean> = emptyMap(),
    val actionFeedbackRes: Int? = null,
)

@HiltViewModel
class UserSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loadUserSearchUseCase: LoadUserSearchUseCase,
    private val detailRepository: DetailRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val keyword: String = savedStateHandle["keyword"] ?: ""

    private val _uiState = MutableStateFlow(UserSearchUiState(keyword = keyword))
    val uiState: StateFlow<UserSearchUiState> = _uiState.asStateFlow()

    private var page = 0
    private var generation = 0
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.authStatus.collect { status ->
                val loggedIn = status == AuthStatus.LOGGED_IN
                if (loggedIn && _uiState.value.users.isEmpty() && !_uiState.value.loading) {
                    reload()
                }
            }
        }
        viewModelScope.launch {
            // 自动重登成功后登录态未变化，但列表已因会话失效而加载失败，重新拉取
            authRepository.sessionRefreshed.collect {
                reload()
            }
        }
        loadFirstPage()
    }

    fun reload() {
        generation++
        page = 0
        _uiState.update {
            it.copy(
                users = emptyList(),
                loading = false,
                loadingMore = false,
                errorRes = null,
                loadMoreErrorRes = null,
                endReached = false,
                searchNeedLogin = false,
                followPendingIds = emptySet(),
                followOverrides = emptyMap(),
            )
        }
        loadFirstPage()
    }

    fun retry() = reload()

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

    /** 关注/取消关注：乐观更新 + 服务端结果回滚/校正 */
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

    private fun loadFirstPage() {
        if (_uiState.value.loading) return
        loadPage(append = false)
    }

    private fun loadPage(append: Boolean) {
        val gen = generation
        val targetPage = if (append) page + 1 else 0
        loadJob?.cancel()
        if (!authRepository.isLoggedIn()) {
            _uiState.update {
                it.copy(
                    loading = false,
                    loadingMore = false,
                    errorRes = null,
                    loadMoreErrorRes = null,
                    endReached = true,
                    searchNeedLogin = true,
                    users = if (append) it.users else emptyList(),
                )
            }
            return
        }
        _uiState.update {
            if (append) it.copy(loadingMore = true, loadMoreErrorRes = null)
            else it.copy(loading = true, errorRes = null, loadMoreErrorRes = null, searchNeedLogin = false)
        }
        loadJob = viewModelScope.launch {
            loadUserSearchUseCase(keyword, targetPage)
                .onSuccess { list ->
                    if (generation != gen) return@launch
                    page = targetPage
                    _uiState.update {
                        it.copy(
                            users = if (append) it.users + list else list,
                            loading = false,
                            loadingMore = false,
                            loadMoreErrorRes = null,
                            endReached = list.isEmpty(),
                        )
                    }
                }
                .onFailure { error ->
                    if (generation != gen) return@launch
                    android.util.Log.d(
                        "PikuDiag",
                        "user search load fail keyword=$keyword append=$append page=$targetPage " +
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
