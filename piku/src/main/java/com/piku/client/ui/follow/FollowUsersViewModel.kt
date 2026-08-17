package com.piku.client.ui.follow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.data.repository.AuthRepository
import com.piku.client.data.repository.DetailRepository
import com.piku.client.data.repository.FollowResult
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.AuthStatus
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.usecase.LoadFollowUsersUseCase
import com.piku.client.ui.common.toFeedErrorRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FollowUsersUiState(
    val users: List<FollowUser> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val errorRes: Int? = null,
    val loadMoreErrorRes: Int? = null,
    val endReached: Boolean = false,
    val followNeedLogin: Boolean = false,
    /** 正在取消关注的用户 ID 集合，防止连点 */
    val unfollowingIds: Set<Long> = emptySet(),
    /** 已取消关注的用户 ID 集合（保留在列表，仅按钮置灰） */
    val unfollowedIds: Set<Long> = emptySet(),
    val actionFeedbackRes: Int? = null,
)

@HiltViewModel
class FollowUsersViewModel @Inject constructor(
    private val loadFollowUsersUseCase: LoadFollowUsersUseCase,
    private val detailRepository: DetailRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FollowUsersUiState())
    val uiState: StateFlow<FollowUsersUiState> = _uiState.asStateFlow()

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
                followNeedLogin = false,
                unfollowingIds = emptySet(),
                unfollowedIds = emptySet(),
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

    fun unfollow(userId: Long) {
        val state = _uiState.value
        if (userId in state.unfollowingIds) return
        _uiState.update { it.copy(unfollowingIds = it.unfollowingIds + userId) }
        viewModelScope.launch {
            val result = detailRepository.updateFollow(userId)
            _uiState.update { s ->
                val stillLoading = s.unfollowingIds - userId
                when (result) {
                    is FollowResult.Unfollowed -> s.copy(
                        unfollowingIds = stillLoading,
                        unfollowedIds = s.unfollowedIds + userId,
                        actionFeedbackRes = R.string.detail_unfollow_sent,
                    )
                    is FollowResult.Followed -> s.copy(
                        unfollowingIds = stillLoading,
                        unfollowedIds = s.unfollowedIds - userId,
                        actionFeedbackRes = R.string.detail_follow_sent,
                    )
                    is FollowResult.Failure -> s.copy(
                        unfollowingIds = stillLoading,
                        actionFeedbackRes = R.string.detail_follow_failed,
                    )
                    else -> s.copy(unfollowingIds = stillLoading)
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
                    followNeedLogin = true,
                    users = if (append) it.users else emptyList(),
                )
            }
            return
        }
        _uiState.update {
            if (append) it.copy(loadingMore = true, loadMoreErrorRes = null)
            else it.copy(loading = true, errorRes = null, loadMoreErrorRes = null, followNeedLogin = false)
        }
        loadJob = viewModelScope.launch {
            loadFollowUsersUseCase(targetPage)
                .onSuccess { resultPage ->
                    if (generation != gen) return@launch
                    page = targetPage
                    val users = if (append) _uiState.value.users + resultPage.users else resultPage.users
                    val total = if (append) _uiState.value.total else resultPage.total
                    _uiState.update {
                        it.copy(
                            users = users,
                            total = total,
                            loading = false,
                            loadingMore = false,
                            loadMoreErrorRes = null,
                            endReached = resultPage.users.isEmpty() || users.size >= total,
                        )
                    }
                }
                .onFailure { error ->
                    if (generation != gen) return@launch
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
