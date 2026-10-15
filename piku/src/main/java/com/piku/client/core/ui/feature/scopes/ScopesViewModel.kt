package com.piku.client.core.ui.feature.scopes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.core.model.WorkSource
import com.piku.client.core.source.MAX_SCOPES
import com.piku.client.core.source.SourceScope
import com.piku.client.core.source.SourceScopeKind
import com.piku.client.core.source.SourceScopeRegistry
import com.piku.client.core.source.SourceScopeSuggestion
import com.piku.client.core.source.SourceScopes
import com.piku.client.core.source.canAddScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 圈层管理页的路由参数：看哪个源的圈层 */
const val ARG_SCOPE_SOURCE = "source"

data class ScopesUiState(
    val kinds: List<SourceScopeKind> = emptyList(),
    /** 当前查的维度（同人/配对/标签），决定联想与热门榜打哪一类 */
    val kind: String = "",
    val scopes: List<SourceScope> = emptyList(),
    val active: SourceScope? = null,
    val query: String = "",
    val results: List<SourceScopeSuggestion> = emptyList(),
    val searching: Boolean = false,
    val searched: Boolean = false,
    val failed: Boolean = false,
    /** 当前维度的热门榜：空查询时的发现入口，不输入也能直接订 */
    val popular: List<SourceScopeSuggestion> = emptyList(),
    val popularLoading: Boolean = false,
) {
    /** 输入了才把整页让给结果；空查询时这页是"我的订阅 + 热门榜" */
    val searchMode: Boolean get() = query.isNotBlank()

    /** 已订阅的不进结果，且实时滤：点过一条它立刻消失 */
    val candidates: List<SourceScopeSuggestion>
        get() = results.filterNot(::subscribed)

    /** 热门榜同理滤掉已订阅的：订过的从榜上消失，退订又回来 */
    val popularCandidates: List<SourceScopeSuggestion>
        get() = popular.filterNot(::subscribed)

    val canAdd: Boolean get() = canAddScope(scopes, MAX_SCOPES)

    private fun subscribed(item: SourceScopeSuggestion): Boolean =
        scopes.any { it.kind == kind && it.name == item.name }
}

/**
 * 「我的圈层」整页：默认管理态（已订阅、点行设为当前、右侧移除），输入后切成搜索态。
 * 联想与落盘都交给该源的 [SourceScopes]，页面不认站点名。
 */
@HiltViewModel
class ScopesViewModel @Inject constructor(
    scopeRegistry: SourceScopeRegistry,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val source = WorkSource.valueOf(savedStateHandle.get<String>(ARG_SCOPE_SOURCE).orEmpty())
    private val store: SourceScopes? = scopeRegistry.byId(source)

    private val _uiState = MutableStateFlow(
        ScopesUiState(
            kinds = store?.kinds.orEmpty(),
            kind = store?.kinds?.firstOrNull()?.id.orEmpty(),
        ),
    )
    val uiState: StateFlow<ScopesUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var popularJob: Job? = null

    init {
        viewModelScope.launch {
            store?.scopes?.collect { list -> _uiState.update { it.copy(scopes = list) } }
        }
        viewModelScope.launch {
            store?.active?.collect { current -> _uiState.update { it.copy(active = current) } }
        }
        loadPopular()
    }

    fun selectKind(kind: String) {
        if (_uiState.value.kind == kind) return
        _uiState.update { it.copy(kind = kind) }
        loadPopular()
        search(_uiState.value.query)
    }

    fun setQuery(query: String) {
        if (_uiState.value.query == query) return
        _uiState.update { it.copy(query = query) }
        search(query)
    }

    fun clearQuery() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(query = "", results = emptyList(), searching = false, searched = false, failed = false)
        }
    }

    /** 搜索失败后的重试：把同一次查询再跑一遍 */
    fun retry() = search(_uiState.value.query)

    /** 热门榜随维度走：换维度先清空再拉，免得旧维度的榜挂在新维度标题下面 */
    private fun loadPopular() {
        val store = store ?: return
        val kind = _uiState.value.kind
        popularJob?.cancel()
        _uiState.update { it.copy(popular = emptyList(), popularLoading = true) }
        popularJob = viewModelScope.launch {
            store.popular(kind)
                .onSuccess { found -> _uiState.update { it.copy(popular = found, popularLoading = false) } }
                .onFailure { _uiState.update { it.copy(popular = emptyList(), popularLoading = false) } }
        }
    }

    /** 添加。首次订阅时顺手设为当前，用户回去不用再点一次 */
    fun add(scope: SourceScope) {
        val store = store ?: return
        // 以落盘的当前值为准，而不是订阅镜像：连点两下时镜像还没回来，读它会互相覆盖
        val current = store.scopes.value
        if (scope in current || !canAddScope(current)) return
        store.setScopes(current + scope)
        if (store.active.value == null) store.setActive(scope)
    }

    fun remove(scope: SourceScope) {
        val store = store ?: return
        store.setScopes(store.scopes.value - scope)
    }

    fun setActive(scope: SourceScope) {
        store?.setActive(scope)
    }

    private fun search(raw: String) {
        searchJob?.cancel()
        val term = raw.trim()
        if (term.isEmpty()) {
            _uiState.update {
                it.copy(results = emptyList(), searching = false, searched = false, failed = false)
            }
            return
        }
        val kind = _uiState.value.kind
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(searching = true, failed = false) }
            // 打字间隔的抖动挡在请求之前：联想是每敲一个字都可能发一次的接口
            delay(SUGGEST_DEBOUNCE_MS)
            store?.suggest(kind, term)
                ?.onSuccess { found -> _uiState.update { it.copy(results = found, searched = true) } }
                ?.onFailure { _uiState.update { it.copy(results = emptyList(), failed = true) } }
            _uiState.update { it.copy(searching = false) }
        }
    }

    private companion object {
        const val SUGGEST_DEBOUNCE_MS = 300L
    }
}
