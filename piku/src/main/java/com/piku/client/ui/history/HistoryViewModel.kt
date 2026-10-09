package com.piku.client.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.domain.model.HistoryItem
import com.piku.client.domain.model.HistoryTimeRange
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkKey
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.model.key
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

/** 列表订阅的键：时间范围走 SQL，来源在内存里过滤 */
private data class HistoryFilterKey(
    val range: HistoryTimeRange,
    val source: WorkSource?,
)

data class HistoryUiState(
    val sections: List<HistorySection> = emptyList(),
    val favoriteIds: Set<WorkKey> = emptySet(),
    val selectedRange: HistoryTimeRange = HistoryTimeRange.ALL,
    val selectedSource: WorkSource? = null,
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

    /** 本次列表视图的顺序快照：页内点开作品不重排，换筛选或重进页面才按最新浏览排 */
    private val orderFreeze = HistoryOrderFreeze()

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState
                .map { HistoryFilterKey(it.selectedRange, it.selectedSource) }
                .distinctUntilChanged()
                .flatMapLatest { filter ->
                    observeHistoryUseCase(filter.range).map { items ->
                        if (filter.source == null) {
                            items
                        } else {
                            items.filter { it.work.source == filter.source }
                        }
                    }
                }
                .map(orderFreeze::freeze)
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
        if (_uiState.value.selectedRange == range) return
        orderFreeze.reset()
        _uiState.update { it.copy(selectedRange = range) }
    }

    fun selectSource(source: WorkSource?) {
        if (_uiState.value.selectedSource == source) return
        orderFreeze.reset()
        _uiState.update { it.copy(selectedSource = source) }
    }

    fun resetFilters() {
        val state = _uiState.value
        if (state.selectedRange == HistoryTimeRange.ALL && state.selectedSource == null) return
        orderFreeze.reset()
        _uiState.update { it.copy(selectedRange = HistoryTimeRange.ALL, selectedSource = null) }
    }

    fun clear() {
        removedStack.clear()
        _uiState.update { it.copy(pendingRemovedCount = 0) }
        viewModelScope.launch { clearHistoryUseCase() }
    }

    fun remove(item: HistoryItem) {
        // 撤销要写回真实浏览时间，列表里的 entry 带的是进入页面时的冻结时间
        val removed = item.copy(visitedAt = orderFreeze.latestTime(item.work.key) ?: item.visitedAt)
        viewModelScope.launch {
            removeHistoryUseCase(item.work)
            if (removedStack.size == MAX_UNDO) removedStack.removeFirst()
            removedStack.addLast(removed)
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
            .groupBy { Instant.ofEpochMilli(it.visitedAt).atZone(zone).toLocalDate() }
            .entries
            .sortedByDescending { it.key }
            .map { (date, list) -> HistorySection(date, list) }
    }

    private companion object {
        const val MAX_UNDO = 20
    }
}

internal class HistoryOrderFreeze {
    private var frozenTimes: Map<WorkKey, Long>? = null
    private var latestTimes: Map<WorkKey, Long> = emptyMap()

    fun reset() {
        frozenTimes = null
    }

    fun latestTime(key: WorkKey): Long? = latestTimes[key]

    fun freeze(items: List<HistoryItem>): List<HistoryItem> {
        val times = items.associate { it.work.key to it.visitedAt }
        latestTimes = times
        val frozen = frozenTimes ?: times.also { frozenTimes = times }
        val fresh = ArrayList<HistoryItem>(items.size)
        val known = ArrayList<HistoryItem>(items.size)
        for (item in items) {
            val frozenAt = frozen[item.work.key]
            if (frozenAt == null) fresh += item else known += item.copy(visitedAt = frozenAt)
        }
        return fresh.sortedByDescending { it.visitedAt } + known.sortedByDescending { it.visitedAt }
    }
}
