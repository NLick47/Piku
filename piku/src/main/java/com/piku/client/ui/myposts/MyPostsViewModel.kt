package com.piku.client.ui.myposts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.data.repository.PublishFailure
import com.piku.client.data.repository.PublishRepository
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.Work
import com.piku.client.domain.usecase.LoadUserWorksUseCase
import com.piku.client.ui.common.FeedbackChannel
import com.piku.client.ui.common.toFeedErrorRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyPostsUiState(
    val userId: Long = -1L,
    val userName: String = "",
    val works: List<Work> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val errorRes: Int? = null,
    val loadMoreErrorRes: Int? = null,
    val endReached: Boolean = false,
    /** 正在删除的作品 id（防连点，对应行给删除中反馈） */
    val deletingId: Long? = null,
)

@HiltViewModel
class MyPostsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loadUserWorksUseCase: LoadUserWorksUseCase,
    private val publishRepository: PublishRepository,
) : ViewModel() {

    private val userId: Long = savedStateHandle["userId"] ?: -1L
    private val userNameArg: String = savedStateHandle["userName"] ?: ""

    private val _uiState = MutableStateFlow(
        MyPostsUiState(userId = userId, userName = userNameArg),
    )
    val uiState: StateFlow<MyPostsUiState> = _uiState.asStateFlow()

    /** 一次性反馈（删除结果） */
    val feedback = FeedbackChannel()

    /**
     * 作品被删除的一次性信号，与 [feedback] 分开是因为它驱动的是「返回个人主页时要刷新列表」
     * 这个副作用：UI 侧在非挂起的收集里立刻写返回标记，不再依赖 snackbar 的展示时序
     * （原先写在 showSnackbar 之后，用户删完立刻回退会被取消，个人主页便不刷新）。
     */
    private val _workDeleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val workDeleted: SharedFlow<Unit> = _workDeleted.asSharedFlow()

    private var page = 0

    companion object {
        /** 与 Routes.KEY_POSTS_CHANGED 同值：编辑保存后由 AppNavHost 写回本页 handle（不 import navigation 层） */
        const val KEY_POSTS_CHANGED = "my_posts_changed"
    }

    init {
        loadFirstPage()
        // 编辑页保存成功后由 AppNavHost 写回标记：整页刷新，保证列表与服务端一致
        viewModelScope.launch {
            savedStateHandle.getStateFlow(KEY_POSTS_CHANGED, false).collect { changed ->
                if (changed) {
                    savedStateHandle[KEY_POSTS_CHANGED] = false
                    retry()
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || state.endReached || state.errorRes != null || state.loadMoreErrorRes != null) return
        loadPage(append = true)
    }

    fun retry() {
        page = 0
        _uiState.update {
            it.copy(
                works = emptyList(),
                loading = false,
                loadingMore = false,
                errorRes = null,
                loadMoreErrorRes = null,
                endReached = false,
            )
        }
        loadPage(append = false)
    }

    fun retryLoadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || state.endReached || state.errorRes != null || state.loadMoreErrorRes == null) return
        _uiState.update { it.copy(loadMoreErrorRes = null) }
        loadPage(append = true)
    }


    /**
     * 删除已发布作品：服务端删除不可撤销，确认框由 UI 层负责。
     * 成功后本地列表直接移除该项。
     */
    fun deleteWork(work: Work) {
        if (_uiState.value.deletingId != null) return
        _uiState.update { it.copy(deletingId = work.id) }
        viewModelScope.launch {
            publishRepository.deleteEntry(work.id)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            deletingId = null,
                            works = it.works.filterNot { w -> w.id == work.id },
                        )
                    }
                    _workDeleted.tryEmit(Unit)
                    feedback.show(R.string.my_posts_deleted)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(deletingId = null) }
                    feedback.show(
                        when (e) {
                            is PublishFailure.NotLoggedIn -> R.string.detail_follow_login_hint
                            else -> R.string.my_posts_delete_failed
                        },
                    )
                }
        }
    }

    private fun loadFirstPage() {
        if (_uiState.value.loading) return
        loadPage(append = false)
    }

    private fun loadPage(append: Boolean) {
        val targetPage = if (append) page + 1 else 0
        _uiState.update {
            if (append) it.copy(loadingMore = true, loadMoreErrorRes = null)
            else it.copy(loading = true, errorRes = null, loadMoreErrorRes = null)
        }
        viewModelScope.launch {
            loadUserWorksUseCase(userId, targetPage)
                .onSuccess { result ->
                    page = targetPage
                    _uiState.update {
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            loadMoreErrorRes = null,
                            works = if (append) it.works + result.works else result.works,
                            endReached = result.works.isEmpty(),
                        )
                    }
                }
                .onFailure { error ->
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
                                errorRes = (error as? AppError)?.toFeedErrorRes(),
                            )
                        }
                    }
                }
        }
    }
}
