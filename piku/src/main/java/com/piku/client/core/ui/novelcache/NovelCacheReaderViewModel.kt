package com.piku.client.core.ui.novelcache

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.piku.client.core.model.WorkKey
import com.piku.client.core.model.WorkSource
import com.piku.client.core.novelcache.CachedNovel
import com.piku.client.core.novelcache.NovelCacheRepository
import com.piku.client.core.storage.prefs.NovelReaderSettings
import com.piku.client.core.storage.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NovelCacheReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NovelCacheRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val key: WorkKey? = runCatching {
        WorkKey(
            source = WorkSource.valueOf(savedStateHandle.get<String>("source").orEmpty()),
            workId = savedStateHandle.get<String>("workId").orEmpty(),
        )
    }.getOrNull()

    val initialPercent: Int = key?.let { settingsRepository.getNovelProgress(it) } ?: 0

    val settings: StateFlow<NovelReaderSettings> = settingsRepository.novelReaderSettings

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    init {
        val workKey = key
        if (workKey == null) {
            _state.value = ReaderState.Missing
        } else {
            viewModelScope.launch {
                val cached = repository.cached(workKey)
                if (cached == null) {
                    _state.value = ReaderState.Missing
                } else {
                    _state.value = ReaderState.Ready(cached)
                }
            }
        }
    }

    fun saveProgress(percent: Int) {
        key?.let { settingsRepository.setNovelProgress(it, percent) }
    }

    fun setSettings(settings: NovelReaderSettings, persist: Boolean) {
        settingsRepository.setNovelReaderSettings(settings, persist)
    }

    sealed interface ReaderState {
        data object Loading : ReaderState
        data object Missing : ReaderState
        data class Ready(val novel: CachedNovel) : ReaderState
    }
}
