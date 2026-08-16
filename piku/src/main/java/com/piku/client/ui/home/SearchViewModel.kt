package com.piku.client.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.domain.usecase.ClearSearchHistoryUseCase
import com.piku.client.domain.usecase.ObserveSearchHistoryUseCase
import com.piku.client.domain.usecase.RecordSearchKeywordUseCase
import com.piku.client.domain.usecase.RemoveSearchKeywordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val observeSearchHistoryUseCase: ObserveSearchHistoryUseCase,
    private val recordSearchKeywordUseCase: RecordSearchKeywordUseCase,
    private val removeSearchKeywordUseCase: RemoveSearchKeywordUseCase,
    private val clearSearchHistoryUseCase: ClearSearchHistoryUseCase,
) : ViewModel() {

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    init {
        viewModelScope.launch {
            observeSearchHistoryUseCase().collect { keywords ->
                _history.value = keywords
            }
        }
    }

    fun record(keyword: String) {
        viewModelScope.launch { recordSearchKeywordUseCase(keyword) }
    }

    fun remove(keyword: String) {
        viewModelScope.launch { removeSearchKeywordUseCase(keyword) }
    }

    fun clearHistory() {
        viewModelScope.launch { clearSearchHistoryUseCase() }
    }
}
