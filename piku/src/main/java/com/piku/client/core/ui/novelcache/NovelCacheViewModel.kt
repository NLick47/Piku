package com.piku.client.core.ui.novelcache

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkKey
import com.piku.client.core.model.WorkSource
import com.piku.client.core.model.decodeWorkId
import com.piku.client.core.novelcache.NovelCacheRepository
import com.piku.client.core.novelcache.NovelCacheSummary
import com.piku.client.core.novelcache.fetchNovelChapters
import com.piku.client.core.source.SourceRegistry
import com.piku.client.core.storage.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import javax.inject.Inject

data class NovelCacheItem(
    val summary: NovelCacheSummary,
    /** 小说读到百分之几；0 = 没读过，100 = 已读完 */
    val progressPercent: Int,
)

enum class NovelCacheSort { RECENT, TITLE, SIZE }

/** 管理页列表的筛选与排序：纯函数，VM 只是把五个流汇进来 */
internal object NovelCacheListOps {

    fun filter(
        items: List<NovelCacheItem>,
        source: WorkSource?,
        query: String,
    ): List<NovelCacheItem> {
        val q = query.trim()
        return items.filter { item ->
            (source == null || item.summary.key.source == source) &&
                (q.isEmpty() ||
                    item.summary.title.contains(q, ignoreCase = true) ||
                    item.summary.authorName.contains(q, ignoreCase = true))
        }
    }

    fun sort(items: List<NovelCacheItem>, sort: NovelCacheSort): List<NovelCacheItem> = when (sort) {
        NovelCacheSort.RECENT -> items.sortedByDescending { it.summary.fetchedAt }
        NovelCacheSort.TITLE -> items.sortedWith(compareBy(Collator.getInstance()) { it.summary.title })
        NovelCacheSort.SIZE -> items.sortedByDescending { it.summary.byteSize }
    }
}

@HiltViewModel
class NovelCacheViewModel @Inject constructor(
    private val repository: NovelCacheRepository,
    private val sourceRegistry: SourceRegistry,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val selectedSource = MutableStateFlow<WorkSource?>(null)
    private val query = MutableStateFlow("")
    private val sortOrder = MutableStateFlow(NovelCacheSort.RECENT)

    val filterSource: StateFlow<WorkSource?> = selectedSource.asStateFlow()
    val filterQuery: StateFlow<String> = query.asStateFlow()
    val sort: StateFlow<NovelCacheSort> = sortOrder.asStateFlow()

    /** 全量列表（带阅读进度）：筛选 chip 的源列表和顶栏计数用它 */
    val allItems: StateFlow<List<NovelCacheItem>> = combine(
        repository.observeSummaries(),
        settingsRepository.readingProgress,
    ) { summaries, progress ->
        summaries.map { summary ->
            NovelCacheItem(
                summary = summary,
                progressPercent = progress[summary.key]?.novelPercent ?: 0,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** 筛选 + 排序后的列表 */
    val items: StateFlow<List<NovelCacheItem>> = combine(
        allItems,
        selectedSource,
        query,
        sortOrder,
    ) { list, source, q, order ->
        NovelCacheListOps.sort(NovelCacheListOps.filter(list, source, q), order)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val totalBytes: StateFlow<Long> = repository.observeTotalBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0L)

    fun selectSource(source: WorkSource?) {
        selectedSource.value = source
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun selectSort(value: NovelCacheSort) {
        sortOrder.value = value
    }

    fun delete(key: WorkKey) {
        viewModelScope.launch { repository.delete(key) }
    }

    fun deleteAll(keys: Collection<WorkKey>) {
        viewModelScope.launch { keys.forEach { repository.delete(it) } }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }

    /** 管理页的「更新到最新」：取网并覆盖这份缓存 */
    fun update(item: NovelCacheItem) {
        viewModelScope.launch {
            val work = item.summary.toWork()
            val source = sourceRegistry.byId(work.source)
            repository.download(work) { fetchNovelChapters(source, work) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

internal fun NovelCacheSummary.toWork(): Work {
    val (id, kind) = decodeWorkId(key.workId)
    return Work(
        id = id,
        authorId = authorId,
        authorName = authorName,
        authorAvatarUrl = null,
        categoryCd = -1,
        categoryName = "",
        title = title,
        thumbnailUrl = "",
        imageCount = 0,
        r18 = false,
        source = key.source,
        kind = kind,
    )
}
