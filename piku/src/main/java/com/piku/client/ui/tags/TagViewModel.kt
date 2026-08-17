package com.piku.client.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.Work
import com.piku.client.domain.usecase.AddCustomTagUseCase
import com.piku.client.domain.usecase.LoadTagFeedUseCase
import com.piku.client.domain.usecase.ObserveCustomTagsUseCase
import com.piku.client.domain.usecase.ObserveFavoriteIdsUseCase
import com.piku.client.domain.usecase.RemoveCustomTagUseCase
import com.piku.client.domain.usecase.ToggleFavoriteUseCase
import com.piku.client.ui.common.toFeedErrorRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TagsUiState(
    val tags: List<String> = emptyList(),
    val loaded: Boolean = false,
    val selectedTag: String? = null,
    val works: List<Work> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val errorRes: Int? = null,
    val loadMoreErrorRes: Int? = null,
    val endReached: Boolean = false,
)

@HiltViewModel
class TagViewModel @Inject constructor(
    private val observeCustomTagsUseCase: ObserveCustomTagsUseCase,
    private val addCustomTagUseCase: AddCustomTagUseCase,
    private val removeCustomTagUseCase: RemoveCustomTagUseCase,
    private val loadTagFeedUseCase: LoadTagFeedUseCase,
    private val observeFavoriteIdsUseCase: ObserveFavoriteIdsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TagsUiState())
    val uiState: StateFlow<TagsUiState> = _uiState.asStateFlow()

    private var page = 0
    private var generation = 0

    init {
        viewModelScope.launch {
            observeCustomTagsUseCase().collect { tags ->
                _uiState.update { it.copy(tags = tags, loaded = true) }
            }
        }
        viewModelScope.launch {
            observeFavoriteIdsUseCase().collect { ids ->
                _uiState.update { it.copy(favoriteIds = ids) }
            }
        }
    }

    /** 点击标签：在本页就地加载该标签的作品（与收藏页点击收藏夹一致） */
    fun selectTag(tag: String) {
        if (tag == _uiState.value.selectedTag) return
        generation++
        page = 0
        _uiState.update {
            it.copy(
                selectedTag = tag,
                works = emptyList(),
                loading = true,
                errorRes = null,
                loadMoreErrorRes = null,
                endReached = false,
            )
        }
        loadPage(append = false)
    }

    fun backToList() {
        generation++
        _uiState.update {
            it.copy(
                selectedTag = null,
                works = emptyList(),
                loading = false,
                errorRes = null,
                loadMoreErrorRes = null,
                endReached = false,
            )
        }
    }

    fun addTag(tag: String) {
        viewModelScope.launch { addCustomTagUseCase(tag) }
    }

    fun removeTag(tag: String) {
        viewModelScope.launch { removeCustomTagUseCase(tag) }
    }

    fun toggleFavorite(work: Work) {
        viewModelScope.launch { toggleFavoriteUseCase(work) }
    }

    fun retry() {
        if (_uiState.value.selectedTag == null) return
        loadPage(append = false)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.selectedTag == null || state.loading || state.loadingMore ||
            state.endReached || state.errorRes != null || state.loadMoreErrorRes != null
        ) {
            return
        }
        loadPage(append = true)
    }

    fun retryLoadMore() {
        val state = _uiState.value
        if (state.selectedTag == null || state.loadMoreErrorRes == null) return
        _uiState.update { it.copy(loadMoreErrorRes = null) }
        loadPage(append = true)
    }

    private fun loadPage(append: Boolean) {
        val tag = _uiState.value.selectedTag ?: return
        val gen = generation
        val targetPage = if (append) page + 1 else 0
        _uiState.update {
            if (append) it.copy(loadingMore = true, loadMoreErrorRes = null)
            else it.copy(loading = true, errorRes = null, loadMoreErrorRes = null)
        }
        viewModelScope.launch {
            loadTagFeedUseCase(tag, targetPage)
                .onSuccess { list ->
                    if (generation != gen) return@launch
                    page = targetPage
                    _uiState.update {
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            loadMoreErrorRes = null,
                            works = if (append) it.works + list else list,
                            endReached = list.isEmpty(),
                        )
                    }
                }
                .onFailure { error ->
                    if (generation != gen) return@launch
                    android.util.Log.d(
                        "PikuDiag",
                        "tag feed load fail tag=$tag append=$append page=$targetPage " +
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
