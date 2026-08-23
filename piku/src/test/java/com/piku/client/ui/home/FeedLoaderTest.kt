package com.piku.client.ui.home

import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.PoipikuCategory
import com.piku.client.domain.model.Work
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedLoaderTest {

    private fun work(id: Long) = Work(
        id = id,
        authorId = id * 100,
        authorName = "author$id",
        authorAvatarUrl = null,
        categoryCd = 0,
        categoryName = "all",
        title = "title$id",
        thumbnailUrl = "https://img/$id.jpg",
        imageCount = 1,
        r18 = false,
    )

    /**
     * 可编排的假数据源：记录请求页码、按队列返回结果；
     * [hold] 可让指定页码的请求挂起（模拟在途），[release] 放行。重复 hold 同页会替换新门。
     */
    private class FakeApi {
        val pages = mutableListOf<Int>()
        val queue = ArrayDeque<Result<List<Work>>>()
        /** 按页号返回（模拟真实服务端分页语义），优先于 [queue] */
        private val pageData = mutableMapOf<Int, List<Work>>()
        private val gates = mutableMapOf<Int, CompletableDeferred<Unit>>()

        suspend fun fetch(page: Int): Result<List<Work>> {
            pages += page
            gates[page]?.let { it.await() }
            pageData[page]?.let { return Result.success(it) }
            return queue.removeFirstOrNull() ?: Result.success(emptyList())
        }

        fun hold(page: Int) {
            gates[page] = CompletableDeferred()
        }

        fun release(page: Int) {
            gates.remove(page)?.complete(Unit)
        }

        fun enqueue(vararg lists: List<Work>) {
            lists.forEach { queue.add(Result.success(it)) }
        }

        fun enqueuePage(page: Int, list: List<Work>) {
            pageData[page] = list
        }

        fun enqueueFailure(error: AppError) {
            queue.add(Result.failure(error))
        }
    }

    private fun TestScope.newLoader(
        api: FakeApi,
        tab: FeedTab = FeedTab.LATEST,
        loggedIn: Boolean = true,
        prefetch: Boolean = false,
    ): FeedLoader = FeedLoader(
        key = FeedKey(tab, PoipikuCategory.ALL, null),
        scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
        fetchPage = api::fetch,
        isLoggedIn = { loggedIn },
        prefetchEnabled = prefetch,
    )

    @Test
    fun refreshSuccessPopulatesState() = runTest {
        val api = FakeApi().apply { enqueue(listOf(work(1), work(2))) }
        val loader = newLoader(api)

        loader.refresh(countNotice = false)
        advanceUntilIdle()

        val s = loader.state.value
        assertEquals(listOf(1L, 2L), s.works.map { it.id })
        assertEquals(0, s.page)
        assertFalse(s.loading)
        assertFalse(s.endReached)
        assertEquals(listOf(0), api.pages)
        assertNull(s.refreshNotice)
    }

    @Test
    fun refreshFailureSetsErrorThenRetryRecovers() = runTest {
        val api = FakeApi().apply {
            enqueueFailure(AppError.Network)
            enqueue(listOf(work(1)))
        }
        val loader = newLoader(api)

        loader.refresh(countNotice = false)
        advanceUntilIdle()
        assertEquals(AppError.Network, loader.state.value.error)
        assertFalse(loader.state.value.loading)

        loader.refresh(countNotice = false)
        advanceUntilIdle()
        assertNull(loader.state.value.error)
        assertEquals(listOf(1L), loader.state.value.works.map { it.id })
    }

    @Test
    fun secondRefreshCancelsFirstInFlight() = runTest {
        val api = FakeApi()
        val loader = newLoader(api)

        // 第一次刷新挂在半途
        api.hold(0)
        loader.refresh(countNotice = false)
        advanceUntilIdle()
        assertTrue(loader.state.value.loading)

        // 第二次刷新取消第一次并正常完成（换新门使自己也挂起，随后放行）
        api.hold(0)
        api.enqueue(listOf(work(2)))
        loader.refresh(countNotice = false)
        api.release(0)
        advanceUntilIdle()
        assertFalse(loader.state.value.loading)

        advanceUntilIdle()
        assertEquals(listOf(2L), loader.state.value.works.map { it.id })
        assertEquals(listOf(0, 0), api.pages.toList())
    }

    @Test
    fun loadMoreAppendsDedupesAndReachesEnd() = runTest {
        val api = FakeApi().apply {
            enqueue(
                listOf(work(1), work(2)),   // 第 0 页
                listOf(work(2), work(3)),   // 预取第 1 页（含重复 id=2）
                emptyList(),                // 预取第 2 页（空 → 到底）
            )
        }
        val loader = newLoader(api, prefetch = true)

        loader.refresh(countNotice = false)
        advanceUntilIdle()

        loader.loadMore() // 快路径消费预取的 [2,3]
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 3L), loader.state.value.works.map { it.id })
        assertEquals(1, loader.state.value.page)

        loader.loadMore() // 快路径消费预取的空页 → 到底
        advanceUntilIdle()
        assertTrue(loader.state.value.endReached)

        val pagesSnapshot = api.pages.toList()
        loader.loadMore() // 到底后不再发请求
        advanceUntilIdle()
        assertEquals(pagesSnapshot, api.pages)
    }

    @Test
    fun loadMoreDuringRefreshIsIgnored() = runTest {
        val api = FakeApi().apply { hold(0) }
        val loader = newLoader(api)

        loader.refresh(countNotice = false)
        advanceUntilIdle()
        loader.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(0), api.pages)
    }

    @Test
    fun refreshDuringLoadMoreDiscardsStaleAppend() = runTest {
        val api = FakeApi().apply {
            enqueue(
                listOf(work(1)),   // 第 0 页
                listOf(work(5)),   // 刷新后的新第 0 页
                listOf(work(9)),   // 被作废的追加第 1 页（挂起中未消费，留在队列）
            )
        }
        val loader = newLoader(api)

        loader.refresh(countNotice = false)
        advanceUntilIdle()

        // 网络追加挂起在第 1 页
        api.hold(1)
        loader.loadMore()
        advanceUntilIdle()
        assertTrue(loader.state.value.loadingMore)

        // 刷新抢占：追加任务被取消，新数据落位
        loader.refresh(countNotice = false)
        advanceUntilIdle()
        api.release(1)
        advanceUntilIdle()

        assertEquals(listOf(5L), loader.state.value.works.map { it.id })
        assertEquals(0, loader.state.value.page)
        assertFalse(loader.state.value.loadingMore)
    }

    @Test
    fun followNotLoggedInShowsLoginGuideWithoutRequest() = runTest {
        val api = FakeApi()
        val loader = newLoader(api, tab = FeedTab.FOLLOW, loggedIn = false)

        loader.refresh(countNotice = false)
        advanceUntilIdle()

        val s = loader.state.value
        assertTrue(s.followNeedLogin)
        assertTrue(s.endReached)
        assertTrue(s.works.isEmpty())
        assertTrue(api.pages.isEmpty())
    }

    @Test
    fun randomIgnoresPagingAndEndsImmediately() = runTest {
        val api = FakeApi().apply { enqueue(listOf(work(1), work(2))) }
        val loader = newLoader(api, tab = FeedTab.RANDOM)

        loader.refresh(countNotice = false)
        advanceUntilIdle()

        assertTrue(loader.state.value.endReached)
        loader.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(0), api.pages)
    }

    @Test
    fun noticeCountsNewItemsBeforeBaseline() = runTest {
        val api = FakeApi().apply {
            enqueue(listOf(work(3), work(4)))
            enqueue(listOf(work(5), work(6), work(3)))
        }
        val loader = newLoader(api)

        loader.refresh(countNotice = false)
        advanceUntilIdle()

        loader.refresh(countNotice = true)
        advanceUntilIdle()
        assertEquals(2, loader.state.value.refreshNotice)
    }

    @Test
    fun zeroNoticeIsGeneratedAndClearable() = runTest {
        val api = FakeApi().apply {
            enqueue(listOf(work(1), work(2)))
            enqueue(listOf(work(1), work(2)))   // 刷新后无变化 → 0 条新增
        }
        val loader = newLoader(api)

        loader.refresh(countNotice = false)
        advanceUntilIdle()
        assertNull(loader.state.value.refreshNotice)

        loader.refresh(countNotice = true)
        advanceUntilIdle()
        assertEquals(0, loader.state.value.refreshNotice)

        loader.clearNotice()
        assertNull(loader.state.value.refreshNotice)
    }

    @Test
    fun noticeStaysClearedAfterSubsequentStateUpdates() = runTest {
        val api = FakeApi().apply {
            enqueue(listOf(work(1), work(2)))
            enqueue(emptyList())   // 预取空页 → 快路径到底
        }
        val loader = newLoader(api, prefetch = true)

        loader.refresh(countNotice = true) // 列表无变化 → 0 条提示
        advanceUntilIdle()
        assertEquals(0, loader.state.value.refreshNotice)

        loader.clearNotice()
        // 后续无关状态变化（追加空页到底）不得让提示复活
        loader.loadMore()
        advanceUntilIdle()

        assertNull(loader.state.value.refreshNotice)
        assertTrue(loader.state.value.endReached)
    }

    @Test
    fun thumbnailUpdateReplacesMatchingOnly() = runTest {
        val api = FakeApi().apply { enqueue(listOf(work(1), work(2))) }
        val loader = newLoader(api)
        loader.refresh(countNotice = false)
        advanceUntilIdle()

        loader.updateThumbnail(2, "https://new/2.jpg")

        var s = loader.state.value
        assertEquals("https://img/1.jpg", s.works[0].thumbnailUrl)
        assertEquals("https://new/2.jpg", s.works[1].thumbnailUrl)

        loader.updateThumbnail(999, "https://x.jpg")
        s = loader.state.value
        assertEquals("https://img/1.jpg", s.works[0].thumbnailUrl)
        assertEquals("https://new/2.jpg", s.works[1].thumbnailUrl)
    }

    @Test
    fun loadMoreWhilePrefetchInFlightDoesNotSkipServerPage() = runTest {
        val api = FakeApi().apply {
            enqueue(listOf(work(1)))          // 第 0 页
            enqueuePage(1, listOf(work(2)))   // 第 1 页（双发同数据：模拟幂等分页）
            enqueuePage(2, listOf(work(9)))   // 第 2 页：修复前会被永久跳过
            hold(1)                           // 预取第 1 页挂在半途
        }
        val loader = newLoader(api, prefetch = true)

        loader.refresh(countNotice = false)
        advanceUntilIdle()

        // 预取未完成时触发加载更多：慢路径与预取同页双发
        loader.loadMore()
        advanceUntilIdle()
        assertTrue(loader.state.value.loadingMore)

        api.hold(2)
        api.release(1)   // 预取先恢复写 prefetched，loadMore 后恢复追加
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), loader.state.value.works.map { it.id })
        assertEquals(1, loader.state.value.page)

        // 快路径消费过期 prefetched 并杀掉在途的第 2 页请求 → 第 2 页内容被跳过
        loader.loadMore()
        advanceUntilIdle()
        api.release(2)
        advanceUntilIdle()

        loader.loadMore()
        advanceUntilIdle()

        assertEquals(
            "第 2 页内容被跳过",
            listOf(1L, 2L, 9L),
            loader.state.value.works.map { it.id },
        )
    }

    @Test
    fun refreshDuringInFlightLoadMoreResetsLoadingMoreFlag() = runTest {
        val api = FakeApi().apply {
            enqueue(listOf(work(1)))
            enqueue(listOf(work(5)))
            hold(1)
        }
        val loader = newLoader(api)

        loader.refresh(countNotice = false)
        advanceUntilIdle()

        loader.loadMore()
        advanceUntilIdle()
        assertTrue(loader.state.value.loadingMore)

        // 刷新打断在途追加：加载指示必须立即复位，否则会转满整个刷新周期
        loader.refresh(countNotice = false)
        assertFalse(loader.state.value.loadingMore)
    }

    @Test
    fun randomRefreshDoesNotProduceNotice() = runTest {
        val api = FakeApi().apply {
            enqueue(listOf(work(1), work(2)))
            enqueue(listOf(work(7), work(8), work(9)))   // 不含基准 id 的随机结果
        }
        val loader = newLoader(api, tab = FeedTab.RANDOM)

        loader.refresh(countNotice = false)
        advanceUntilIdle()

        loader.refresh(countNotice = true)
        advanceUntilIdle()

        assertNull("随机流无时间序，不应产生新增提示", loader.state.value.refreshNotice)
    }
}
