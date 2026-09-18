package com.piku.client.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.domain.model.HistoryItem
import com.piku.client.domain.model.HistoryTimeRange
import com.piku.client.domain.model.Work
import com.piku.client.domain.usecase.ClearHistoryUseCase
import com.piku.client.domain.usecase.ObserveFavoriteIdsUseCase
import com.piku.client.domain.usecase.ObserveHistoryUseCase
import com.piku.client.domain.usecase.RemoveHistoryUseCase
import com.piku.client.domain.usecase.RestoreHistoryUseCase
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** 同一天浏览的记录归为一组，组内按浏览时间倒序 */
data class HistorySection(
    val date: LocalDate,
    val items: List<HistoryItem>,
)

data class HistoryUiState(
    val sections: List<HistorySection> = emptyList(),
    val favoriteIds: Set<Long> = emptySet(),
    val selectedRange: HistoryTimeRange = HistoryTimeRange.ALL,
    val loaded: Boolean = false,
    /** 待恢复的删除条数：>0 时页面底部常驻撤销条，不自动消失 */
    val pendingRemovedCount: Int = 0,
) {
    val count: Int get() = sections.sumOf { it.items.size }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val observeHistoryUseCase: ObserveHistoryUseCase,
    private val clearHistoryUseCase: ClearHistoryUseCase,
    private val removeHistoryUseCase: RemoveHistoryUseCase,
    private val restoreHistoryUseCase: RestoreHistoryUseCase,
    private val observeFavoriteIdsUseCase: ObserveFavoriteIdsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
) : ViewModel() {

    /** 最近删除的记录，后进先出；连续删多条时可以一条条撤回来 */
    private val removedStack = ArrayDeque<HistoryItem>()

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState
                .map { it.selectedRange }
                .distinctUntilChanged()
                .flatMapLatest { range -> observeHistoryUseCase(range) }
                .map(::groupByDate)
                .collect { sections ->
                    _uiState.update { it.copy(sections = sections, loaded = true) }
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
        removedStack.clear()
        _uiState.update { it.copy(pendingRemovedCount = 0) }
        viewModelScope.launch { clearHistoryUseCase() }
    }

    fun remove(item: HistoryItem) {
        viewModelScope.launch {
            removeHistoryUseCase(item.work.id)
            if (removedStack.size == MAX_UNDO) removedStack.removeFirst()
            removedStack.addLast(item)
            _uiState.update { it.copy(pendingRemovedCount = removedStack.size) }
        }
    }

    fun undoRemove() {
        val item = removedStack.removeLastOrNull() ?: return
        _uiState.update { it.copy(pendingRemovedCount = removedStack.size) }
        viewModelScope.launch { restoreHistoryUseCase(item.work, item.visitedAt) }
    }

    /** 关掉撤销条：剩下的不再可恢复 */
    fun dismissRemovedNotice() {
        removedStack.clear()
        _uiState.update { it.copy(pendingRemovedCount = 0) }
    }

    fun toggleFavorite(work: Work) {
        viewModelScope.launch { toggleFavoriteUseCase(work) }
    }

    private fun groupByDate(items: List<HistoryItem>): List<HistorySection> {
        val zone = ZoneId.systemDefault()
        return items
            .groupBy { LocalDate.ofInstant(Instant.ofEpochMilli(it.visitedAt), zone) }
            .entries
            .sortedByDescending { it.key }
            .map { (date, list) -> HistorySection(date, list) }
    }

    private companion object {
        const val MAX_UNDO = 20
    }
}
