package com.piku.client.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.domain.model.HistoryTimeRange
import com.piku.client.domain.model.Work
import com.piku.client.domain.usecase.ClearHistoryUseCase
import com.piku.client.domain.usecase.ObserveFavoriteIdsUseCase
import com.piku.client.domain.usecase.ObserveHistoryUseCase
import com.piku.client.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val works: List<Work> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val selectedRange: HistoryTimeRange = HistoryTimeRange.ALL,
    val loaded: Boolean = false,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val observeHistoryUseCase: ObserveHistoryUseCase,
    private val clearHistoryUseCase: ClearHistoryUseCase,
    private val observeFavoriteIdsUseCase: ObserveFavoriteIdsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState
                .map { it.selectedRange }
                .distinctUntilChanged()
                .flatMapLatest { range -> observeHistoryUseCase(range) }
                .collect { works ->
                    _uiState.update { it.copy(works = works, loaded = true) }
                }
        }
        viewModelScope.launch {
            observeFavoriteIdsUseCase().collect { ids ->
                _uiState.update { it.copy(favoriteIds = ids) }
            }
        }
    }

    fun selectRange(range: HistoryTimeRange) {
        _uiState.update { it.copy(selectedRange = range) }
    }

    fun clear() {
        viewModelScope.launch { clearHistoryUseCase() }
    }

    fun toggleFavorite(work: Work) {
        viewModelScope.launch { toggleFavoriteUseCase(work) }
    }
}
