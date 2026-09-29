package com.piku.client.ui.source

import com.piku.client.R
import com.piku.client.data.local.InMemorySharedPreferences
import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.ContentSource
import com.piku.client.domain.source.SourceFacet
import com.piku.client.domain.source.SourceFeed
import com.piku.client.domain.source.SourcePage
import com.piku.client.domain.source.SourceRegistry
import com.piku.client.domain.source.SourceWorkPage
import com.piku.client.domain.source.SourceWorkOpen
import com.piku.client.domain.usecase.ObserveHomeSourceUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SourceFeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun work(id: Long) = Work(
        id = id,
        authorId = id,
        authorName = "a$id",
        authorAvatarUrl = null,
        categoryCd = 0,
        categoryName = "",
        title = "t$id",
        thumbnailUrl = "https://img/$id.jpg",
        imageCount = 1,
        r18 = false,
        source = WorkSource.PIXIV,
    )

    /** 两个流（其一要登录）、两个维度；取页记录请求并返回可编排的结果 */
    private class FakeSource : ContentSource {
        override val id = WorkSource.PIXIV
        override val labelRes = R.string.home_source_pixiv
        override val feeds = listOf(
            SourceFeed(id = "popular", labelRes = R.string.pixiv_tab_daily),
            SourceFeed(id = "secret", labelRes = R.string.pixiv_tab_weekly, requiresLogin = true),
        )
        var facetsOverride: List<SourceFacet>? = null
        override val facets: List<SourceFacet>
            get() = facetsOverride ?: listOf(
                SourceFacet(id = "all", labelRes = R.string.pixiv_filter_all, selectedByDefault = true),
                SourceFacet(id = "illust", labelRes = R.string.pixiv_filter_illust),
            )

        /** (feedId, facetId, page) 的全部取页请求 */
        val pages = mutableListOf<Triple<String, String?, Int>>()
        var items: List<Work> = emptyList()
        var totalPages: Int? = null
        var outcome: Result<Unit> = Result.success(Unit)
        var openRoute: SourceWorkOpen = SourceWorkOpen.InAppViewer
        var viewerPages: List<String> = listOf("https://img.example/1.jpg")

        override suspend fun page(feedId: String, facetId: String?, page: Int): Result<SourcePage> {
            pages.add(Triple(feedId, facetId, page))
            return outcome.map { SourcePage(items = items, totalPages = totalPages) }
        }

        override fun open(work: Work): SourceWorkOpen = openRoute

        override suspend fun workPages(work: Work): Result<List<SourceWorkPage>> =
            Result.success(viewerPages.map { SourceWorkPage(it) })
    }

    private fun build(
        fake: FakeSource,
        loggedIn: Boolean = true,
        startSource: WorkSource = WorkSource.PIXIV,
    ): Pair<SourceFeedViewModel, SettingsRepository> {
        val settings = SettingsRepository(InMemorySharedPreferences())
        settings.setHomeSource(startSource)
        val vm = SourceFeedViewModel(
            sourceRegistry = SourceRegistry(setOf(fake)),
            observeHomeSourceUseCase = ObserveHomeSourceUseCase(settings),
            settingsRepository = settings,
            isLoggedIn = { loggedIn },
            config = SourceFeedConfig(prefetchEnabled = false),
        )
        dispatcher.scheduler.advanceUntilIdle()
        return vm to settings
    }

    @Test
    fun declarationsAndFirstPageArriveAfterSourceSelection() {
        val fake = FakeSource().apply {
            items = listOf(work(1), work(2))
            totalPages = 5
        }
        val (vm, _) = build(fake)

        val s = vm.ui.value
        assertEquals(WorkSource.PIXIV, s.source)
        assertEquals(fake.feeds, s.feeds)
        assertEquals(fake.facets, s.facets)
        // 声明序第一流 + selectedByDefault 维度
        assertEquals("popular", s.feedId)
        assertEquals("all", s.facetId)
        assertEquals(listOf(1L, 2L), s.items.map { it.id })
        assertFalse(s.endReached)
        assertEquals(listOf(Triple("popular", "all", 0)), fake.pages)
    }

    @Test
    fun facetSwitchReloadsWithSameFeed() {
        val fake = FakeSource().apply { items = listOf(work(1)) }
        val (vm, _) = build(fake)

        vm.selectFacet("illust")
        dispatcher.scheduler.advanceUntilIdle()

        val s = vm.ui.value
        assertEquals("popular", s.feedId)
        assertEquals("illust", s.facetId)
        assertEquals(
            listOf(
                Triple("popular", "all", 0),
                Triple("popular", "illust", 0),
            ),
            fake.pages,
        )
    }

    /** 维度切走再切回命中缓存的 loader（loader 即内存缓存）：不重新取页，条目回首次结果 */
    @Test
    fun facetSwitchBackReusesCachedLoaderWithoutRefetch() {
        val fake = FakeSource().apply { items = listOf(work(1)) }
        val (vm, _) = build(fake)

        vm.selectFacet("illust")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, fake.pages.size)

        vm.selectFacet("all")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, fake.pages.size)
        assertEquals("all", vm.ui.value.facetId)
        assertEquals(listOf(1L), vm.ui.value.items.map { it.id })
    }

    /** 要登录的流在未登录时不得发请求：服务端只会回登录页 */
    @Test
    fun loginGatedFeedSendsNoRequestWhenLoggedOut() {
        val fake = FakeSource().apply { items = listOf(work(1)) }
        val (vm, _) = build(fake, loggedIn = false)

        vm.selectFeed("secret")
        dispatcher.scheduler.advanceUntilIdle()

        val s = vm.ui.value
        assertTrue(s.needLogin)
        assertTrue(s.items.isEmpty())
        assertEquals(1, fake.pages.size) // 只有初始流那一页
    }

    @Test
    fun loginGatedFeedLoadsWhenLoggedIn() {
        val fake = FakeSource().apply { items = listOf(work(9)) }
        val (vm, _) = build(fake, loggedIn = true)

        vm.selectFeed("secret")
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.ui.value.needLogin)
        assertEquals(listOf(9L), vm.ui.value.items.map { it.id })
        assertEquals("secret", vm.ui.value.feedId)
    }

    /** 总页数探明后 loadMore 本地短路：不再发请求，直接到底 */
    @Test
    fun loadMoreShortCircuitsOnceTotalPagesKnown() {
        val fake = FakeSource().apply {
            items = listOf(work(1), work(2))
            totalPages = 1
        }
        val (vm, _) = build(fake)

        vm.loadMore()
        dispatcher.scheduler.advanceUntilIdle()

        val s = vm.ui.value
        assertTrue(s.endReached)
        assertEquals(listOf(1L, 2L), s.items.map { it.id })
        assertEquals(1, fake.pages.size)
    }

    @Test
    fun loadMoreAppendsPagesWhenTotalPagesUnknown() {
        val fake = FakeSource().apply { items = listOf(work(1)) }
        val (vm, _) = build(fake)
        vm.loadMore()
        dispatcher.scheduler.advanceUntilIdle()
        // 同一条目重复返回会被引擎按 id 去重
        assertEquals(listOf(1L), vm.ui.value.items.map { it.id })
        assertEquals(2, fake.pages.size)
        assertFalse(vm.ui.value.endReached)
    }

    @Test
    fun failedFirstPageRecoversOnRetry() {
        val fake = FakeSource().apply {
            outcome = Result.failure(AppError.Network)
        }
        val (vm, _) = build(fake)

        assertTrue(vm.ui.value.failed)

        fake.outcome = Result.success(Unit)
        fake.items = listOf(work(3))
        vm.retry()
        dispatcher.scheduler.advanceUntilIdle()

        val s = vm.ui.value
        assertFalse(s.failed)
        assertEquals(listOf(3L), s.items.map { it.id })
    }

    /** 无默认标记的维度取第一个：有维度必有选中，UI 显示与取参才不会差一档 */
    @Test
    fun facetWithoutDefaultFallsBackToFirst() {
        val fake = FakeSource().apply {
            items = listOf(work(1))
            facetsOverride = listOf(
                SourceFacet(id = "illust", labelRes = R.string.pixiv_filter_illust),
                SourceFacet(id = "manga", labelRes = R.string.pixiv_filter_manga),
            )
        }
        val (vm, _) = build(fake)

        assertEquals("illust", vm.ui.value.facetId)
        assertEquals(listOf(Triple("popular", "illust", 0)), fake.pages)
    }

    /** 点开作品的去向由源声明，壳只透传不解释 */
    @Test
    fun openRoutesBySourceDeclaration() {
        val fake = FakeSource().apply { items = listOf(work(1)) }
        val (vm, _) = build(fake)
        val work = vm.ui.value.items.first()

        assertEquals(SourceWorkOpen.InAppViewer, vm.open(work))

        fake.openRoute = SourceWorkOpen.External("https://example.com/${work.id}")
        assertEquals(SourceWorkOpen.External("https://example.com/${work.id}"), vm.open(work))
    }

    /** R-18 门的数据源：成人开关的当前值要能到达壳状态（初始值也算） */
    @Test
    fun adultToggleReachesShellState() {
        val fake = FakeSource().apply { items = listOf(work(1)) }
        val settings = SettingsRepository(InMemorySharedPreferences())
        settings.setShowAdultContent(true)
        settings.setHomeSource(WorkSource.PIXIV)
        val vm = SourceFeedViewModel(
            sourceRegistry = SourceRegistry(setOf(fake)),
            observeHomeSourceUseCase = ObserveHomeSourceUseCase(settings),
            settingsRepository = settings,
            isLoggedIn = { true },
            config = SourceFeedConfig(prefetchEnabled = false),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.ui.value.adultEnabled)
    }

    /** poipiku 是主源，由 HomeViewModel 的壳负责：通用壳不得为它建加载器 */
    @Test
    fun poipikuSelectionNeverTriggersLoads() {
        val fake = FakeSource()
        val (vm, _) = build(fake, startSource = WorkSource.POIPIKU)

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(WorkSource.POIPIKU, vm.ui.value.source)
        assertTrue(fake.pages.isEmpty())
        assertTrue(vm.ui.value.items.isEmpty())
    }
}
