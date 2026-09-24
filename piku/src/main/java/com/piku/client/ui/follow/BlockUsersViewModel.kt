package com.piku.client.ui.follow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.data.repository.AuthRepository
import com.piku.client.data.repository.reloadOnSessionChange
import com.piku.client.data.repository.BlockListRepository
import com.piku.client.data.repository.BlockResult
import com.piku.client.data.repository.DetailRepository
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.AuthStatus
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.usecase.LoadBlockUsersUseCase
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

data class BlockUsersUiState(
    val users: List<FollowUser> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val errorRes: Int? = null,
    val loadMoreErrorRes: Int? = null,
    val endReached: Boolean = false,
    val needLogin: Boolean = false,
    /** 正在解除屏蔽的用户 ID 集合，防止连点 */
    val unblockingIds: Set<Long> = emptySet(),
)

@HiltViewModel
class BlockUsersViewModel @Inject constructor(
    private val loadBlockUsersUseCase: LoadBlockUsersUseCase,
    private val detailRepository: DetailRepository,
    private val blockListRepository: BlockListRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlockUsersUiState())
    val uiState: StateFlow<BlockUsersUiState> = _uiState.asStateFlow()

    /**
     * 操作反馈走一次性事件通道而非 UiState：本页 VM 挂在 Home 的 ViewModelStore 上
     * （全屏 Dialog 继承宿主作用域），关闭页面不会销毁——存在 State 里的反馈会在
     * 重进页面时被重放一遍
     */
    val feedback = FeedbackChannel()

    private var page = 0
    private var generation = 0
    private var loadJob: Job? = null

    init {
        // 本地名单是列表展示的唯一来源：屏蔽动作实时写入本地，不必等服务端
        // （BlockListF 有缓存延迟）。服务端拉取只负责补充昵称/头像与推进分页。
        viewModelScope.launch {
            blockListRepository.entries.collect { list ->
                _uiState.update { s -> s.copy(users = list.toFollowUsers()) }
            }
        }
        // 会话一变就重拉：重登成功后要补回失效期间拉不到的数据，登出后名单已由
        // clearSession 清空，再拉一次拿到的是未登录态
        viewModelScope.reloadOnSessionChange(authRepository.sessionVersion) { reload() }
        loadFirstPage()
    }

    fun reload() {
        generation++
        page = 0
        _uiState.update {
            it.copy(
                // 保留本地名单：刷新（或刷新失败）时列表不该变空——本地是展示的唯一来源
                users = blockListRepository.entries.value.toFollowUsers(),
                loading = false,
                loadingMore = false,
                errorRes = null,
                loadMoreErrorRes = null,
                endReached = false,
                needLogin = false,
                unblockingIds = emptySet(),
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

    /**
     * 解除屏蔽。列表由本地名单驱动（[BlockListRepository.remove] 触发收集器刷新），
     * 这里只负责按钮态与结果反馈。
     */
    fun unblock(userId: Long) {
        val state = _uiState.value
        if (userId in state.unblockingIds) return
        _uiState.update { it.copy(unblockingIds = it.unblockingIds + userId) }
        viewModelScope.launch {
            val result = detailRepository.updateBlock(userId, blocked = false)
            _uiState.update { s ->
                s.copy(unblockingIds = s.unblockingIds - userId)
            }
            feedback.show(
                if (result is BlockResult.Unblocked) {
                    R.string.block_users_unblocked
                } else {
                    // 未登录（会话已失效）与请求失败都归为失败反馈，避免静默无响应
                    R.string.block_users_unblock_failed
                },
            )
        }
    }

    /** 本地名单 → 列表项：屏蔽列表的展示由本地名单唯一驱动 */
    private fun List<BlockListRepository.Entry>.toFollowUsers(): List<FollowUser> =
        map { FollowUser(it.userId, it.name, it.avatarUrl) }

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
                    // 未登录时按账号维度不可用：即使本地有残留名单也走登录引导，
                    // 免得用户在无会话状态点到"解除"（必然失败）
                    needLogin = true,
                )
            }
            return
        }
        _uiState.update {
            if (append) it.copy(loadingMore = true, loadMoreErrorRes = null)
            else it.copy(loading = true, errorRes = null, loadMoreErrorRes = null, needLogin = false)
        }
        loadJob = viewModelScope.launch {
            loadBlockUsersUseCase(targetPage)
                .onSuccess { users ->
                    if (generation != gen) return@launch
                    page = targetPage
                    // 并集合并（不覆盖本地）：服务端有缓存延迟，且分页每次只带一页。
                    // 列表展示由本地名单收集器驱动，这里不再直接写 users（避免重复 key）
                    blockListRepository.mergeFromServer(users)
                    _uiState.update {
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            loadMoreErrorRes = null,
                            // BlockListF 不返回 TOTAL，返回空列表即到末页
                            endReached = users.isEmpty(),
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
