package com.piku.client.ui.follow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.data.repository.reloadOnSessionChange
import com.piku.client.data.source.PixivFollowsSource
import com.piku.client.data.source.PoipikuFollowsSource
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.source.SourceFollows
import com.piku.client.ui.common.FeedbackChannel
import com.piku.client.ui.common.toFeedErrorRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FollowTab { PUBLIC, QUIET }

data class FollowUsersUiState(
    val tab: FollowTab = FollowTab.PUBLIC,
    val quietSupported: Boolean = false,
    val users: List<FollowUser> = emptyList(),
    val total: Int? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val errorRes: Int? = null,
    val loadMoreErrorRes: Int? = null,
    val endReached: Boolean = false,
    val followNeedLogin: Boolean = false,
    /** 正在切换关注的用户 ID 集合，防止连点 */
    val unfollowingIds: Set<Long> = emptySet(),
    /** 已取消关注的用户 ID 集合（保留在列表，仅按钮置灰） */
    val unfollowedIds: Set<Long> = emptySet(),
)

/**
 * 我的关注页的状态机，与源无关：取页与关注/取关都走 [SourceFollows] 能力。
 * 具体源由各源的抽屉插件挑下面的 Hilt 子类绑定，壳子 [FollowUsersScreen] 只认这个基类。
 *
 * 双段（公开/悄悄）各自独立分页；切段时有快照就即时回显，没有就现场拉第一页。
 */
abstract class FollowUsersViewModel(
    private val follows: SourceFollows,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FollowUsersUiState(quietSupported = follows.quietSupported),
    )
    val uiState: StateFlow<FollowUsersUiState> = _uiState.asStateFlow()

    /** 一次性反馈（本页 VM 挂在 Home 作用域，关闭页面不销毁，见 [FeedbackChannel]） */
    val feedback = FeedbackChannel()

    /** 当前段已加载到的页码 */
    private var page = 0
    private var generation = 0
    private var loadJob: Job? = null

    /** 各段快照：切段即时回显；reload（含会话变化）整体作废 */
    private val tabSnapshots = mutableMapOf<FollowTab, TabSnapshot>()

    private data class TabSnapshot(
        val page: Int,
        val users: List<FollowUser>,
        val total: Int?,
        val endReached: Boolean,
    )

    init {
        // 会话一变就重拉：重登成功后要补回失效期间拉不到的数据，
        // 登出后不能继续显示上一个账号的关注列表（本页 VM 挂在 Home 作用域，不随页面销毁）
        viewModelScope.reloadOnSessionChange(follows.sessionVersion) { reload() }
        loadFirstPage()
    }

    fun reload() {
        generation++
        page = 0
        tabSnapshots.clear()
        _uiState.update {
            it.copy(
                users = emptyList(),
                total = null,
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

    /** 切段：当前段存快照，目标段有快照即时回显、没有就拉第一页 */
    fun selectTab(tab: FollowTab) {
        val state = _uiState.value
        if (tab == state.tab || !state.quietSupported) return
        generation++
        // 只快照已就绪的段（加载中/带错的段不存档）：否则切回会恢复出
        // 「空白、非 loading、未到底」的死状态——滚动立即触发 loadMore，
        // append 算出的 targetPage = page + 1 会跳过第 0 页
        if (!state.loading && !state.loadingMore && state.errorRes == null && state.loadMoreErrorRes == null) {
            tabSnapshots[state.tab] = TabSnapshot(page, state.users, state.total, state.endReached)
        }
        val restored = tabSnapshots[tab]
        if (restored == null) {
            page = 0
            _uiState.update {
                it.copy(
                    tab = tab,
                    users = emptyList(),
                    total = null,
                    loading = false,
                    loadingMore = false,
                    errorRes = null,
                    loadMoreErrorRes = null,
                    endReached = false,
                )
            }
            loadFirstPage()
        } else {
            page = restored.page
            _uiState.update {
                it.copy(
                    tab = tab,
                    users = restored.users,
                    total = restored.total,
                    endReached = restored.endReached,
                    loading = false,
                    loadingMore = false,
                    errorRes = null,
                    loadMoreErrorRes = null,
                )
            }
        }
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

    /**
     * 行内关注/取关切换。期望方向由当前行状态给出（已取关的行点的是重新关注）；
     * 服务端回报的**实际**最终态落账——有的源（poipiku）切换接口不收方向。
     */
    fun toggleFollow(userId: Long) {
        val state = _uiState.value
        if (userId in state.unfollowingIds) return
        val follow = userId in state.unfollowedIds
        _uiState.update { it.copy(unfollowingIds = it.unfollowingIds + userId) }
        viewModelScope.launch {
            val result = follows.setFollowed(userId, follow)
            result
                .onSuccess { followedNow ->
                    _uiState.update { s ->
                        s.copy(
                            unfollowingIds = s.unfollowingIds - userId,
                            unfollowedIds = if (followedNow) s.unfollowedIds - userId else s.unfollowedIds + userId,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { s -> s.copy(unfollowingIds = s.unfollowingIds - userId) }
                }
            feedback.show(
                when {
                    result.isSuccess && result.getOrDefault(follow) -> R.string.detail_follow_sent
                    result.isSuccess -> R.string.detail_unfollow_sent
                    else -> R.string.detail_follow_failed
                },
            )
        }
    }

    private fun loadFirstPage() {
        if (_uiState.value.loading) return
        loadPage(append = false)
    }

    private fun loadPage(append: Boolean) {
        val gen = generation
        val targetPage = if (append) page + 1 else 0
        loadJob?.cancel()
        if (!follows.loggedIn) {
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
            follows.follows(targetPage, quiet = _uiState.value.tab == FollowTab.QUIET)
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
                            endReached = resultPage.users.isEmpty() ||
                                (total != null && users.size >= total),
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

/** poipiku 的绑定：列表走关注设置页解析，切换走 UpdateFollowUserF */
@HiltViewModel
class PoipikuFollowUsersViewModel @Inject constructor(
    follows: PoipikuFollowsSource,
) : FollowUsersViewModel(follows)

/** pixiv 的绑定：列表走 v1/user/following（公开/悄悄双段），切换走 v1/user/follow/add|delete */
@HiltViewModel
class PixivFollowUsersViewModel @Inject constructor(
    follows: PixivFollowsSource,
) : FollowUsersViewModel(follows)
