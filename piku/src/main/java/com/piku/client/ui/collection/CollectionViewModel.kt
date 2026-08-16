package com.piku.client.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.data.repository.FavoriteRepository
import com.piku.client.domain.model.FavoriteFolder
import com.piku.client.domain.model.Work
import com.piku.client.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectionUiState(
    val folders: List<FavoriteFolder> = emptyList(),
    val selectedFolderId: Long? = null,
    val selectedFolderName: String = "",
    val works: List<Work> = emptyList(),
    val loaded: Boolean = false,
    val movedToFolder: String? = null,
    val actionFeedbackRes: Int? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionUiState())
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            favoriteRepository.observeFolders().collect { folders ->
                _uiState.update { it.copy(folders = folders, loaded = true) }
            }
        }
        viewModelScope.launch {
            _uiState.map { it.selectedFolderId }
                .distinctUntilChanged()
                .flatMapLatest { folderId ->
                    if (folderId == null) {
                        flowOf(emptyList())
                    } else {
                        favoriteRepository.observeFolderWorks(folderId)
                    }
                }
                .collect { works ->
                    _uiState.update { it.copy(works = works) }
                }
        }
    }

    fun selectFolder(folder: FavoriteFolder) {
        _uiState.update {
            it.copy(
                selectedFolderId = folder.id,
                selectedFolderName = folder.name,
                works = emptyList(),
            )
        }
    }

    fun backToFolders() {
        _uiState.update {
            it.copy(selectedFolderId = null, selectedFolderName = "", works = emptyList())
        }
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            favoriteRepository.createFolder(trimmed)
        }
    }

    fun renameFolder(folderId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            favoriteRepository.renameFolder(folderId, trimmed)
            // 若正在查看该收藏夹，同步更新标题
            if (_uiState.value.selectedFolderId == folderId) {
                _uiState.update { it.copy(selectedFolderName = trimmed) }
            }
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            favoriteRepository.deleteFolder(folderId)
            // 删除的是当前查看的收藏夹时返回列表
            if (_uiState.value.selectedFolderId == folderId) {
                backToFolders()
            }
        }
    }

    fun removeWorkFromFolder(folderId: Long, workId: Long) {
        viewModelScope.launch {
            favoriteRepository.removeFromFolder(workId.toString(), folderId)
            _uiState.update { it.copy(actionFeedbackRes = R.string.collection_removed) }
        }
    }

    fun moveWork(work: Work, fromFolderId: Long, toFolderId: Long, toFolderName: String) {
        viewModelScope.launch {
            favoriteRepository.moveWork(work, fromFolderId, toFolderId)
            _uiState.update { it.copy(movedToFolder = toFolderName) }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(movedToFolder = null, actionFeedbackRes = null) }
    }
}
