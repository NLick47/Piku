package com.piku.client.ui.follow

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.data.local.ImageSaver
import com.piku.client.data.repository.AuthRepository
import com.piku.client.data.repository.reloadOnSessionChange
import com.piku.client.data.repository.BlockListRepository
import com.piku.client.data.repository.BlockResult
import com.piku.client.data.repository.DetailRepository
import com.piku.client.data.repository.FollowResult
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.AuthStatus
import com.piku.client.domain.model.UserPageInfo
import com.piku.client.domain.model.Work
import com.piku.client.domain.usecase.LoadUserWorksUseCase
import com.piku.client.domain.usecase.ObserveFavoriteIdsUseCase
import com.piku.client.domain.usecase.ToggleFavoriteUseCase
import com.piku.client.ui.common.FeedbackChannel
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
    val pageInfo: UserPageInfo? = null,
    val works: List<Work> = emptyList(),
    val favoriteIds: Set<Long> = emptySet(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val errorRes: Int? = null,
    val loadMoreErrorRes: Int? = null,
    val endReached: Boolean = false,
    /** 当前是否已登录（决定关注按钮显示与可用性） */
    val loggedIn: Boolean = false,
    /** 是否当前登录用户自己的主页（不显示关注按钮） */
    val isSelf: Boolean = false,
    val followed: Boolean = true,
    /** 关注操作进行中（防连点） */
    val followSending: Boolean = false,
    /** 是否已屏蔽该用户（用户主页 UserInfoCmdBlock 的 Selected 类） */
    val blocked: Boolean = false,
    /** 屏蔽操作进行中 */
    val blockSending: Boolean = false,
)

@HiltViewModel
class UserWorksViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loadUserWorksUseCase: LoadUserWorksUseCase,
    private val observeFavoriteIdsUseCase: ObserveFavoriteIdsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val detailRepository: DetailRepository,
    private val blockListRepository: BlockListRepository,
    private val authRepository: AuthRepository,
    private val imageSaver: ImageSaver,
) : ViewModel() {

    private val userId: Long = savedStateHandle["userId"] ?: -1L
    private val userNameArg: String = savedStateHandle["userName"] ?: ""

    private val _uiState = MutableStateFlow(
        UserWorksUiState(
            userId = userId,
            userName = userNameArg,
            // 初始假设已关注（关注列表入口）；第一页加载后由页面 Selected 标记覆盖为真实状态
            followed = true,
        ),
    )
    val uiState: StateFlow<UserWorksUiState> = _uiState.asStateFlow()

    /** 一次性反馈（关注/屏蔽操作结果） */
    val feedback = FeedbackChannel()

    private var page = 0
    private var generation = 0

    companion object {
        const val KEY_POSTS_CHANGED = "my_posts_changed"
    }

    init {
        // 从投稿管理页返回：删除过作品就整页刷新，保证列表与服务端一致
        viewModelScope.launch {
            savedStateHandle.getStateFlow(KEY_POSTS_CHANGED, false).collect { changed ->
                if (changed) {
                    savedStateHandle[KEY_POSTS_CHANGED] = false
                    retry()
                }
            }
        }
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
        // マイボックス失效时服务端回的是 200 + 空 body，列表已被当成空、
        // endReached 也被误置为 true；登录/登出/重登成功都要重拉第一页
        viewModelScope.reloadOnSessionChange(authRepository.sessionVersion) { retry() }
        viewModelScope.launch {
            authRepository.userProfile.collect { profile ->
                _uiState.update { it.copy(isSelf = profile?.uid?.toLongOrNull() == userId) }
            }
        }
        viewModelScope.launch {
            // 屏蔽名单在别处变化时同步本页（典型：详情页屏蔽该作者后返回本页，或去屏蔽列表
            // 解除后返回）。本页只在第一页拉取时读一次 pageInfo.blocked，之后是内存态，
            // 不跟这个流就会停留在旧状态：作品列表还在、菜单还显示"屏蔽该用户"。
            blockListRepository.blockedIds.collect { ids ->
                val blocked = userId in ids
                if (blocked == _uiState.value.blocked) return@collect
                _uiState.update { s ->
                    s.copy(
                        blocked = blocked,
                        // 屏蔽后服务端只回无作品的壳页：本地一并清空，避免看起来"没生效"
                        works = if (blocked) emptyList() else s.works,
                        endReached = if (blocked) true else s.endReached,
                        // 服务端屏蔽会连带解除关注
                        followed = if (blocked) false else s.followed,
                    )
                }
                // 解除屏蔽后作品需要重新拉取（此前列表已清空）
                if (!blocked) retry()
            }
        }
        loadFirstPage()
    }

    fun toggleFavorite(work: Work) {
        viewModelScope.launch { toggleFavoriteUseCase(work) }
    }

    /** 关注/取消关注作者：未登录提示登录，操作中防连点；自己的主页不显示按钮 */
    fun toggleFollow() {
        val state = _uiState.value
        if (state.isSelf) return
        if (state.followSending) return
        if (!state.loggedIn) {
            feedback.show(R.string.detail_follow_login_hint)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(followSending = true) }
            val result = detailRepository.updateFollow(userId)
            _uiState.update { s ->
                s.copy(
                    followSending = false,
                    followed = when (result) {
                        FollowResult.Followed -> true
                        FollowResult.Unfollowed -> false
                        else -> s.followed
                    },
                )
            }
            feedback.show(
                when (result) {
                    FollowResult.Followed -> R.string.detail_follow_sent
                    FollowResult.Unfollowed -> R.string.detail_unfollow_sent
                    FollowResult.NotLoggedIn -> R.string.detail_follow_login_hint
                    is FollowResult.Failure -> R.string.detail_follow_failed
                },
            )
        }
    }

    fun toggleBlock() {
        val state = _uiState.value
        if (state.isSelf) return
        if (state.blockSending) return
        if (!state.loggedIn) {
            feedback.show(R.string.detail_block_login_hint)
            return
        }
        val target = !state.blocked
        viewModelScope.launch {
            _uiState.update { it.copy(blockSending = true) }
            val result = detailRepository.updateBlock(
                userId,
                target,
                name = state.userName.ifBlank { state.pageInfo?.userName.orEmpty() },
                avatarUrl = state.pageInfo?.avatarUrl,
            )
            _uiState.update { s ->
                s.copy(
                    blockSending = false,
                    blocked = when (result) {
                        BlockResult.Blocked -> true
                        BlockResult.Unblocked -> false
                        else -> s.blocked
                    },
                    followed = if (result is BlockResult.Blocked) false else s.followed,
                    works = if (result is BlockResult.Blocked) emptyList() else s.works,
                    endReached = if (result is BlockResult.Blocked) true else s.endReached,
                )
            }
            feedback.show(
                when (result) {
                    BlockResult.Blocked -> R.string.detail_block_sent
                    BlockResult.Unblocked -> R.string.detail_unblock_sent
                    BlockResult.NotLoggedIn -> R.string.detail_block_login_hint
                    is BlockResult.Failure -> R.string.detail_block_failed
                },
            )
            // 解除屏蔽后作品需要重新拉取
            if (result is BlockResult.Unblocked) retry()
        }
    }

    suspend fun saveAvatar(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return runCatching { imageSaver.save(url, "Piku_avatar") }.isSuccess
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
                            // 页头信息只在第一页刷新，避免分页响应覆盖（分页 pageInfo 为 null）；
                            pageInfo = result.pageInfo ?: it.pageInfo,
                            followed = result.pageInfo?.followed ?: it.followed,
                            blocked = result.pageInfo?.blocked ?: it.blocked,
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
