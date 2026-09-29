package com.piku.client.ui.home

import com.piku.client.data.repository.ThumbnailResolver
import com.piku.client.data.source.PoipikuContentSource
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.PoipikuCategory
import com.piku.client.domain.model.Work
import com.piku.client.domain.source.SourceFeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class FeedKey(
    val tab: FeedTab,
    val category: PoipikuCategory,
)

/** 首页 tab ↔ poipiku 源的流 id。映射放在 ui 层，免得 domain/data 反过来依赖 FeedTab */
internal val FeedTab.sourceId: String
    get() = when (this) {
        FeedTab.HOT -> PoipikuContentSource.FEED_HOT
        FeedTab.LATEST -> PoipikuContentSource.FEED_LATEST
        FeedTab.FOLLOW -> PoipikuContentSource.FEED_FOLLOW
        FeedTab.RANDOM -> PoipikuContentSource.FEED_RANDOM
    }

internal data class FeedSnapshot<T : Any>(
    val items: List<T> = emptyList(),
    val page: Int = 0,
    val endReached: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: AppError? = null,
    val loadMoreError: AppError? = null,
    val needLogin: Boolean = false,
    val refreshNotice: Int? = null,
)

private const val MAX_ITEMS = 600

internal class FeedLoader<K : Any, T : Any>(
    val key: K,
    private val feed: SourceFeed,
    private val scope: CoroutineScope,
    /** 拉取指定页 */
    private val fetchPage: suspend (page: Int) -> Result<List<T>>,
    private val isLoggedIn: () -> Boolean,
    /** 条目身份，用于去重、上限裁剪与「新增 N 条」的基准比对 */
    private val idOf: (T) -> Long,
    /** 预取下一页开关；单测可关闭以便确定性编排 */
    private val prefetchEnabled: Boolean = true,
) {
    private val _state = MutableStateFlow(FeedSnapshot<T>())
    val state: StateFlow<FeedSnapshot<T>> = _state.asStateFlow()

    private var page = 0
    private var refreshJob: Job? = null
    private var loadMoreJob: Job? = null
    private var prefetchJob: Job? = null
    private var prefetched: List<T>? = null
    /** 下拉刷新是否要计算“新增 N 条”提示，以及对比基准（刷新前的首条 id） */
    private var pendingNoticeRequested = false
    private var pendingNoticeBaselineId: Long? = null

    /**
     * 首屏加载 / 下拉刷新 / 失败重试统一入口。原子语义：先取消本 loader 在途任务再重启。
     * [countNotice]=true 时按当前首条 id 计算刷新后新增条数。
     */
    fun refresh(countNotice: Boolean) {
        // 无时间序的流首条 id 基准比对无意义：不计算新增提示
        pendingNoticeRequested = countNotice && feed.chronological
        pendingNoticeBaselineId = _state.value.items.firstOrNull()?.let(idOf)
        refreshJob?.cancel()
        loadMoreJob?.cancel()
        cancelPrefetch()
        startRefresh()
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached || s.error != null || s.loadMoreError != null) return
        val next = prefetched
        if (next != null) {
            cancelPrefetch()
            page += 1
            appendPage(page, next)
            maybePrefetch()
        } else {
            startLoadMore()
        }
    }

    fun retryLoadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached || s.error != null || s.loadMoreError == null) return
        _state.update { it.copy(loadMoreError = null) }
        startLoadMore()
    }

    fun clearNotice() {
        if (_state.value.refreshNotice == null) return
        _state.update { it.copy(refreshNotice = null) }
    }

    /**
     * 就地替换某一条目（密码作品解锁后回填缩略图等），其余内容保持不变。
     * 是否该替换由调用方判断——判定条件往往是站点专属的。
     */
    fun updateItem(match: (T) -> Boolean, transform: (T) -> T) {
        if (_state.value.items.none(match)) return
        _state.update { s -> s.copy(items = s.items.map { if (match(it)) transform(it) else it }) }
    }

    /** 停掉本 loader 的全部在途任务（整体失效或被 LRU 逐出时调用） */
    fun dispose() {
        refreshJob?.cancel()
        loadMoreJob?.cancel()
        cancelPrefetch()
    }

    private fun startRefresh() {
        if (feed.requiresLogin && !isLoggedIn()) {
            // 需要登录的流：不发请求（服务端会返回登录页），直接展示登录引导
            _state.update {
                it.copy(
                    items = emptyList(),
                    endReached = true,
                    loading = false,
                    loadingMore = false,
                    error = null,
                    loadMoreError = null,
                    needLogin = true,
                    refreshNotice = null,
                )
            }
            return
        }
        _state.update {
            it.copy(loading = true, loadingMore = false, error = null, loadMoreError = null, needLogin = false)
        }
        refreshJob = scope.launch {
            fetchPage(0)
                .onSuccess { list ->
                    page = 0
                    val notice = takePendingNotice(list)
                    _state.update {
                        it.copy(
                            items = capItems(list),
                            page = 0,
                            endReached = !feed.paginated || list.isEmpty(),
                            loading = false,
                            loadingMore = false,
                            error = null,
                            loadMoreError = null,
                            refreshNotice = notice,
                        )
                    }
                    maybePrefetch()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            error = error as? AppError,
                            refreshNotice = null,
                        )
                    }
                }
        }
    }

    /** 取出待计算的新增提示并复位标记（只消费一次） */
    private fun takePendingNotice(list: List<T>): Int? {
        if (!pendingNoticeRequested) return null
        pendingNoticeRequested = false
        val baseline = pendingNoticeBaselineId
        pendingNoticeBaselineId = null
        return if (baseline != null) list.takeWhile { idOf(it) != baseline }.size else 0
    }

    private fun startLoadMore() {
        if (feed.requiresLogin && !isLoggedIn()) return
        // 作废在途预取：否则同页双发后过期的 prefetched 会被快路径重复消费，
        // 并杀掉合法的下一页请求，导致分页漂移、服务端内容被永久跳过
        cancelPrefetch()
        _state.update { it.copy(loadingMore = true, loadMoreError = null) }
        val targetPage = page + 1
        loadMoreJob = scope.launch {
            fetchPage(targetPage)
                .onSuccess { list ->
                    // 中途若发生 refresh（loading 标记已被重置），丢弃过期追加结果
                    if (!_state.value.loadingMore) return@launch
                    page = targetPage
                    appendPage(page, list)
                    maybePrefetch()
                }
                .onFailure { error ->
                    if (!_state.value.loadingMore) return@launch
                    _state.update {
                        it.copy(loadingMore = false, loadMoreError = error as? AppError)
                    }
                }
        }
    }

    private fun appendPage(newPage: Int, list: List<T>) {
        _state.update {
            it.copy(
                items = capItems(it.items + list),
                page = newPage,
                endReached = list.isEmpty(),
                loadingMore = false,
            )
        }
    }

    private fun cancelPrefetch() {
        prefetchJob?.cancel()
        prefetched = null
    }

    /** 后台预取下一页：上滑到尾部时 loadMore 可即时消费 */
    private fun maybePrefetch() {
        if (!prefetchEnabled) return
        if (!feed.paginated) return
        if (feed.requiresLogin && !isLoggedIn()) return
        if (_state.value.endReached) return
        val nextPage = page + 1
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            fetchPage(nextPage)
                .onSuccess { list ->
                    // 期间发生过 refresh（page 已变），预取结果作废
                    if (page != nextPage - 1) return@launch
                    prefetched = list
                }
        }
    }

    private fun capItems(list: List<T>): List<T> {
        val unique = list.distinctBy(idOf)
        return if (unique.size > MAX_ITEMS) unique.takeLast(MAX_ITEMS) else unique
    }
}

internal fun FeedLoader<FeedKey, Work>.backfillThumbnail(
    workId: Long,
    thumbnailUrl: String,
) = updateItem(
    match = { work ->
        work.id == workId &&
            work.thumbnailUrl != thumbnailUrl &&
            ThumbnailResolver.needsThumbnailBackfill(work.thumbnailUrl)
    },
    transform = { it.copy(thumbnailUrl = thumbnailUrl) },
)
