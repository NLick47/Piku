package com.piku.client.ui.publish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.data.local.DraftRepository
import com.piku.client.domain.model.PublishDraft
import com.piku.client.domain.model.UploadKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


enum class DraftFilter { ALL, NOVEL, ILLUST }

@HiltViewModel
class DraftBoxViewModel @Inject constructor(
    private val draftRepository: DraftRepository,
) : ViewModel() {

    private val _drafts = MutableStateFlow<List<PublishDraft>>(emptyList())
    val drafts: StateFlow<List<PublishDraft>> = _drafts.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(DraftFilter.ALL)
    val filter: StateFlow<DraftFilter> = _filter.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _drafts.value = draftRepository.drafts()
    }

    fun setQuery(q: String) {
        _query.value = q.take(50)
    }

    fun setFilter(f: DraftFilter) {
        _filter.value = f
    }

    fun delete(id: Long) = viewModelScope.launch {
        draftRepository.delete(id)
        refresh()
    }

    /** 复制副本，返回新 id（调用方可按需直接打开） */
    suspend fun duplicate(id: Long): Long? {
        val newId = draftRepository.duplicate(id)
        refresh()
        return newId
    }

    /** 列表页展示用：搜索 + 过滤后的结果（内存过滤，草稿量小） */
    fun visible(all: List<PublishDraft>, q: String, f: DraftFilter): List<PublishDraft> {
        val key = q.trim().lowercase()
        return all.asSequence()
            .filter {
                when (f) {
                    DraftFilter.ALL -> true
                    DraftFilter.NOVEL -> it.kind == UploadKind.NOVEL
                    DraftFilter.ILLUST -> it.kind == UploadKind.ILLUST
                }
            }
            .filter {
                if (key.isEmpty()) return@filter true
                it.title.lowercase().contains(key) ||
                    it.tags.lowercase().contains(key) ||
                    it.body.lowercase().contains(key)
            }
            .toList()
    }
}
