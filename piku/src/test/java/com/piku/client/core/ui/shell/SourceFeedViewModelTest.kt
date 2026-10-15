package com.piku.client.core.ui.shell

import com.piku.client.R
import com.piku.client.core.storage.prefs.InMemorySharedPreferences
import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.core.model.AppError
import com.piku.client.core.model.AuthStatus
import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkKey
import com.piku.client.core.model.WorkSource
import com.piku.client.core.source.ContentSource
import com.piku.client.core.source.ShellFavorites
import com.piku.client.core.source.SourceAuth
import com.piku.client.core.source.SourceAuthRegistry
import com.piku.client.core.source.SourceFacet
import com.piku.client.core.source.SourceFacetGroup
import com.piku.client.core.source.SourceFacetStyle
import com.piku.client.core.source.SourceFeed
import com.piku.client.core.source.SourcePage
import com.piku.client.core.source.SourceScopes
import com.piku.client.core.source.SourceScopeKind
import com.piku.client.core.source.SourceScopeRegistry
import com.piku.client.core.source.SourceScope
import com.piku.client.core.source.SourceScopeSuggestion
import com.piku.client.core.source.SourceRegistry
import com.piku.client.core.source.SourceWorkPage
import com.piku.client.core.source.SourceWorkOpen
import com.piku.client.core.usecase.ObserveHomeSourceUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /** 壳的收藏能力假实现：键流可编排，切换调用记账 */
    private class FakeFavorites : ShellFavorites {
        override val favoriteIds = MutableStateFlow(emptySet<WorkKey>())
        val toggled = mutableListOf<Work>()
        override suspend fun toggle(work: Work): Boolean {
            toggled += work
            return true
        }
    }

    /** 登录插件的假实现：测试里可以随时翻转登录态，验证门屏跟着变 */
    private class FakeAuth(
        override val source: WorkSource,
        loggedIn: Boolean,
    ) : SourceAuth {
        private val _status = MutableStateFlow(
            if (loggedIn) AuthStatus.LOGGED_IN else AuthStatus.LOGGED_OUT,
        )
        override val status: StateFlow<AuthStatus> = _status.asStateFlow()
        override val loginRoute: String = "fake_login"
        override val logoutMessageRes = 0

        fun setLoggedIn(value: Boolean) {
            _status.value = if (value) AuthStatus.LOGGED_IN else AuthStatus.LOGGED_OUT
        }

        override fun logout() = setLoggedIn(false)
    }

    /** 两个流（其一要登录）、两个维度；取页记录请求并返回可编排的结果 */
    private class FakeSource : ContentSource {
        override val id = WorkSource.PIXIV
        override val labelRes = R.string.home_source_pixiv
        var feedsOverride: List<SourceFeed>? = null
        override val feeds: List<SourceFeed>
            get() = feedsOverride ?: listOf(
                SourceFeed(id = "popular", labelRes = R.string.pixiv_tab_daily),
                SourceFeed(id = "secret", labelRes = R.string.pixiv_tab_weekly, requiresLogin = true),
            )
        var facetsOverride: List<SourceFacetGroup>? = null
        override val facets: List<SourceFacetGroup>
            get() = facetsOverride ?: listOf(
                SourceFacetGroup(
                    id = "type",
                    style = SourceFacetStyle.Dropdown,
                    options = listOf(
                        SourceFacet(id = "all", labelRes = R.string.pixiv_filter_all, selectedByDefault = true),
                        SourceFacet(id = "illust", labelRes = R.string.pixiv_filter_illust),
                    ),
                ),
            )

        /** (feedId, facets, page) 的全部取页请求 */
        val pages = mutableListOf<Triple<String, Map<String, String>, Int>>()

        /** 与 pages 一一对应的圈层 id（不声明圈层的源恒为 null） */
        val requestedScopes = mutableListOf<String?>()
        var items: List<Work> = emptyList()
        var totalPages: Int? = null
        var outcome: Result<Unit> = Result.success(Unit)
        var openRoute: SourceWorkOpen = SourceWorkOpen.InAppViewer
        var viewerPages: List<String> = listOf("https://img.example/1.jpg")

        override suspend fun page(
            feedId: String,
            facets: Map<String, String>,
            page: Int,
            scope: SourceScope?,
        ): Result<SourcePage> {
            pages.add(Triple(feedId, facets, page))
            requestedScopes.add(scope?.id)
            return outcome.map { SourcePage(items = items, totalPages = totalPages) }
        }

        override fun open(work: Work): SourceWorkOpen = openRoute

        override suspend fun workPages(work: Work): Result<List<SourceWorkPage>> =
            Result.success(viewerPages.map { SourceWorkPage(it) })
    }

    private fun build(
        fake: FakeSource,
        auth: FakeAuth = FakeAuth(WorkSource.PIXIV, loggedIn = true),
        startSource: WorkSource = WorkSource.PIXIV,
        scopes: SourceScopes? = null,
    ): SourceFeedViewModel {
        val settings = SettingsRepository(InMemorySharedPreferences())
        settings.setHomeSource(startSource)
        val vm = SourceFeedViewModel(
            sourceRegistry = SourceRegistry(setOf(fake)),
            observeHomeSourceUseCase = ObserveHomeSourceUseCase(settings),
            favorites = FakeFavorites(),
            sourceAuth = SourceAuthRegistry(setOf(auth)),
            scopeRegistry = SourceScopeRegistry(scopes?.let { setOf(it) } ?: emptySet()),
            config = SourceFeedConfig(prefetchEnabled = false),
        )
        dispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    /** 圈层式首页的源侧能力：内存版 store（订阅/激活都即时生效） */
    private class FakeScopes(
        override val sourceId: WorkSource = WorkSource.PIXIV,
        override val kinds: List<SourceScopeKind> = listOf(
            SourceScopeKind("fandom", R.string.ao3_scope_fandom),
        ),
    ) : SourceScopes {
        private val _scopes = MutableStateFlow<List<SourceScope>>(emptyList())
        private val _active = MutableStateFlow<SourceScope?>(null)
        override val scopes: StateFlow<List<SourceScope>> = _scopes
        override val active: StateFlow<SourceScope?> = _active
        override suspend fun suggest(kind: String, term: String) =
            Result.success(listOf(SourceScopeSuggestion("$term-1")))
        override fun setScopes(scopes: List<SourceScope>) {
            _scopes.value = scopes.distinctBy { it.id }
            if (_active.value?.id !in _scopes.value.map { it.id }) {
                _scopes.value.firstOrNull()?.let { _active.value = it }
            }
        }
        override fun setActive(scope: SourceScope) {
            _active.value = scope
        }
    }

    @Test
    fun declarationsAndFirstPageArriveAfterSourceSelection() {
        val fake = FakeSource().apply {
            items = listOf(work(1), work(2))
            totalPages = 5
        }
        val vm = build(fake)

        val s = vm.ui.value
        assertEquals(WorkSource.PIXIV, s.source)
        assertEquals(fake.feeds, s.feeds)
        assertEquals(fake.facets, s.facets)
        // 声明序第一流 + selectedByDefault 维度
        assertEquals("popular", s.feedId)
        assertEquals(mapOf("type" to "all"), s.facetChoices)
        assertEquals(listOf(1L, 2L), s.items.map { it.id })
        assertFalse(s.endReached)
        assertEquals(listOf(Triple("popular", mapOf("type" to "all"), 0)), fake.pages)
    }

    @Test
    fun facetSwitchReloadsWithSameFeed() {
        val fake = FakeSource().apply { items = listOf(work(1)) }
        val vm = build(fake)

        vm.selectFacet("type", "illust")
        dispatcher.scheduler.advanceUntilIdle()

        val s = vm.ui.value
        assertEquals("popular", s.feedId)
        assertEquals(mapOf("type" to "illust"), s.facetChoices)
        assertEquals(
            listOf(
                Triple("popular", mapOf("type" to "all"), 0),
                Triple("popular", mapOf("type" to "illust"), 0),
            ),
            fake.pages,
        )
    }

    /** 维度切走再切回命中缓存的 loader（loader 即内存缓存）：不重新取页，条目回首次结果 */
    @Test
    fun facetSwitchBackReusesCachedLoaderWithoutRefetch() {
        val fake = FakeSource().apply { items = listOf(work(1)) }
        val vm = build(fake)

        vm.selectFacet("type", "illust")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, fake.pages.size)

        vm.selectFacet("type", "all")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, fake.pages.size)
        assertEquals(mapOf("type" to "all"), vm.ui.value.facetChoices)
        assertEquals(listOf(1L), vm.ui.value.items.map { it.id })
    }

    /** 要登录的流在未登录时不得发请求：服务端只会回登录页 */
    @Test
    fun loginGatedFeedSendsNoRequestWhenLoggedOut() {
        val fake = FakeSource().apply { items = listOf(work(1)) }
        val vm = build(fake, FakeAuth(WorkSource.PIXIV, loggedIn = false))

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
        val vm = build(fake, FakeAuth(WorkSource.PIXIV, loggedIn = true))

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
        val vm = build(fake)

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
        val vm = build(fake)
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
        val vm = build(fake)

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
                SourceFacetGroup(
                    id = "type",
                    style = SourceFacetStyle.Dropdown,
                    options = listOf(
                        SourceFacet(id = "illust", labelRes = R.string.pixiv_filter_illust),
                        SourceFacet(id = "manga", labelRes = R.string.pixiv_filter_manga),
                    ),
                ),
            )
        }
        val vm = build(fake)

        assertEquals(mapOf("type" to "illust"), vm.ui.value.facetChoices)
        assertEquals(listOf(Triple("popular", mapOf("type" to "illust"), 0)), fake.pages)
    }

    /** 点开作品的去向由源声明，壳只透传不解释 */
    @Test
    fun openRoutesBySourceDeclaration() {
        val fake = FakeSource().apply { items = listOf(work(1)) }
        val vm = build(fake)
        val work = vm.ui.value.items.first()

        assertEquals(SourceWorkOpen.InAppViewer, vm.open(work))

        fake.openRoute = SourceWorkOpen.External("https://example.com/${work.id}")
        assertEquals(SourceWorkOpen.External("https://example.com/${work.id}"), vm.open(work))
    }

    /** poipiku 是主源，由 HomeViewModel 的壳负责：通用壳不得为它建加载器 */
    @Test
    fun poipikuSelectionNeverTriggersLoads() {
        val fake = FakeSource()
        val vm = build(fake, startSource = WorkSource.POIPIKU)

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(WorkSource.POIPIKU, vm.ui.value.source)
        assertTrue(fake.pages.isEmpty())
        assertTrue(vm.ui.value.items.isEmpty())
    }

    /** 默认流取声明序第一个可用的：占位流、未登录时的登录门流都跳过 */
    @Test
    fun defaultFeedSkipsComingSoonAndLoginGatedFeeds() {
        val fake = FakeSource().apply {
            items = listOf(work(1))
            feedsOverride = listOf(
                SourceFeed(id = "recommend", labelRes = R.string.pixiv_tab_recommend, comingSoon = true),
                SourceFeed(id = "secret", labelRes = R.string.pixiv_tab_weekly, requiresLogin = true),
                SourceFeed(id = "ranking", labelRes = R.string.pixiv_tab_ranking, ranked = true),
            )
        }
        val vm = build(fake, FakeAuth(WorkSource.PIXIV, loggedIn = false))

        assertEquals("ranking", vm.ui.value.feedId)
        assertTrue("榜单流要给壳带名次标记", vm.ui.value.ranked)
        assertEquals(listOf(Triple("ranking", mapOf("type" to "all"), 0)), fake.pages)
    }

    /** 占位流：不发请求也不建加载器，壳展示"即将上线" */
    @Test
    fun comingSoonFeedNeverFetchesAndFlagsState() {
        val fake = FakeSource().apply {
            feedsOverride = listOf(
                SourceFeed(id = "recommend", labelRes = R.string.pixiv_tab_recommend, comingSoon = true),
                SourceFeed(id = "ranking", labelRes = R.string.pixiv_tab_ranking),
            )
        }
        val vm = build(fake)
        fake.pages.clear()

        vm.selectFeed("recommend")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.ui.value.comingSoon)
        assertTrue("占位流不得发请求", fake.pages.isEmpty())

        vm.selectFeed("ranking")
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.ui.value.comingSoon)
    }

    /** 多组维度：切一组不动另一组，取页拿到的是两组的完整组合 */
    @Test
    fun switchingOneFacetGroupKeepsOthers() {
        val fake = FakeSource().apply {
            items = listOf(work(1))
            facetsOverride = listOf(
                SourceFacetGroup(
                    id = "period",
                    style = SourceFacetStyle.Chips,
                    options = listOf(
                        SourceFacet(id = "daily", labelRes = R.string.pixiv_tab_daily, selectedByDefault = true),
                        SourceFacet(id = "weekly", labelRes = R.string.pixiv_tab_weekly),
                    ),
                ),
                SourceFacetGroup(
                    id = "type",
                    style = SourceFacetStyle.Dropdown,
                    options = listOf(
                        SourceFacet(id = "all", labelRes = R.string.pixiv_filter_all, selectedByDefault = true),
                        SourceFacet(id = "manga", labelRes = R.string.pixiv_filter_manga),
                    ),
                ),
            )
        }
        val vm = build(fake)

        assertEquals(mapOf("period" to "daily", "type" to "all"), vm.ui.value.facetChoices)

        vm.selectFacet("period", "weekly")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(mapOf("period" to "weekly", "type" to "all"), vm.ui.value.facetChoices)
        assertEquals(
            listOf(
                Triple("popular", mapOf("period" to "daily", "type" to "all"), 0),
                Triple("popular", mapOf("period" to "weekly", "type" to "all"), 0),
            ),
            fake.pages,
        )
    }

    /** 收藏接线：键（带源）流到达壳状态；点心形调用的就是注入的切换 */
    @Test
    fun favoritesFlowReachesShellAndToggleIsForwarded() {
        val fake = FakeSource().apply { items = listOf(work(1)) }
        val favorites = FakeFavorites()
        val settings = SettingsRepository(InMemorySharedPreferences())
        settings.setHomeSource(WorkSource.PIXIV)
        val vm = SourceFeedViewModel(
            sourceRegistry = SourceRegistry(setOf(fake)),
            observeHomeSourceUseCase = ObserveHomeSourceUseCase(settings),
            favorites = favorites,
            sourceAuth = SourceAuthRegistry(setOf(FakeAuth(WorkSource.PIXIV, loggedIn = true))),
            scopeRegistry = SourceScopeRegistry(emptySet()),
            config = SourceFeedConfig(prefetchEnabled = false),
        )
        dispatcher.scheduler.advanceUntilIdle()

        favorites.favoriteIds.value = setOf(WorkKey(WorkSource.PIXIV, "1"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf(WorkKey(WorkSource.PIXIV, "1")), vm.ui.value.favoriteIds)

        vm.toggleFavorite(work(1))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(1L), favorites.toggled.map { it.id })
    }

    /** 门屏的点「去登录」要知道跳哪：路由由源的登录插件声明，壳只透传 */
    @Test
    fun loginGateCarriesSourceLoginRoute() {
        val fake = FakeSource().apply { items = listOf(work(1)) }
        val vm = build(fake, FakeAuth(WorkSource.PIXIV, loggedIn = false))

        vm.selectFeed("secret")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.ui.value.needLogin)
        assertEquals("fake_login", vm.ui.value.loginRoute)
    }

    /** 在那个页面登录成功后回到本页：门必须自己打开并取数，不该等用户手动刷新 */
    @Test
    fun loginUnlocksGatedFeedAndLoadsIt() {
        val fake = FakeSource().apply {
            items = listOf(work(7))
            feedsOverride = listOf(
                SourceFeed(id = "popular", labelRes = R.string.pixiv_tab_ranking),
                SourceFeed(id = "secret", labelRes = R.string.pixiv_tab_weekly, requiresLogin = true),
            )
        }
        val auth = FakeAuth(WorkSource.PIXIV, loggedIn = false)
        val vm = build(fake, auth)

        vm.selectFeed("secret")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.ui.value.needLogin)
        assertEquals(1, fake.pages.size)

        auth.setLoggedIn(true)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.ui.value.needLogin)
        assertEquals("secret", vm.ui.value.feedId)
        assertEquals(listOf(7L), vm.ui.value.items.map { it.id })
        assertEquals(2, fake.pages.size)
    }

    /** 登出也要立刻反映：内容清掉、门重新挂上 */
    @Test
    fun logoutClosesGatedFeedAgain() {
        val fake = FakeSource().apply {
            items = listOf(work(7))
            feedsOverride = listOf(
                SourceFeed(id = "popular", labelRes = R.string.pixiv_tab_ranking),
                SourceFeed(id = "secret", labelRes = R.string.pixiv_tab_weekly, requiresLogin = true),
            )
        }
        val auth = FakeAuth(WorkSource.PIXIV, loggedIn = true)
        val vm = build(fake, auth)

        vm.selectFeed("secret")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(7L), vm.ui.value.items.map { it.id })

        auth.setLoggedIn(false)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.ui.value.needLogin)
        assertTrue(vm.ui.value.items.isEmpty())
    }

    @Test
    fun pendingAfterLoginFeedShowsComingSoonAfterLogin() {
        val fake = FakeSource().apply {
            items = listOf(work(1))
            feedsOverride = listOf(
                SourceFeed(id = "popular", labelRes = R.string.pixiv_tab_ranking),
                SourceFeed(
                    id = "follow",
                    labelRes = R.string.pixiv_tab_follow_new,
                    requiresLogin = true,
                    pendingAfterLogin = true,
                ),
            )
        }
        val auth = FakeAuth(WorkSource.PIXIV, loggedIn = false)
        val vm = build(fake, auth)

        vm.selectFeed("follow")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.ui.value.needLogin)
        assertFalse(vm.ui.value.comingSoon)
        assertEquals(1, fake.pages.size)

        auth.setLoggedIn(true)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.ui.value.comingSoon)
        assertFalse(vm.ui.value.needLogin)
        assertTrue(vm.ui.value.items.isEmpty())
        assertEquals("登录后也不该为占位流发请求", 1, fake.pages.size)
    }

    /** 没注册登录插件的源：门照挂，但没有可跳的路由（外壳据此不显示按钮） */
    @Test
    fun unregisteredSourceHasNoLoginRoute() {
        val fake = FakeSource().apply { items = listOf(work(1)) }
        val settings = SettingsRepository(InMemorySharedPreferences())
        settings.setHomeSource(WorkSource.PIXIV)
        val vm = SourceFeedViewModel(
            sourceRegistry = SourceRegistry(setOf(fake)),
            observeHomeSourceUseCase = ObserveHomeSourceUseCase(settings),
            favorites = FakeFavorites(),
            sourceAuth = SourceAuthRegistry(emptySet()),
            scopeRegistry = SourceScopeRegistry(emptySet()),
            config = SourceFeedConfig(prefetchEnabled = false),
        )
        dispatcher.scheduler.advanceUntilIdle()

        vm.selectFeed("secret")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.ui.value.needLogin)
        assertNull(vm.ui.value.loginRoute)
    }

    // ---- 圈层式首页 ----

    /** 一个圈层都没订阅：不发请求，状态是"去设置圈层"而不是空列表 */
    @Test
    fun scopedSourceWithoutSubscriptionsAsksForSetupInsteadOfFetching() {
        val fake = FakeSource().apply { items = listOf(work(1)) }
        val scopes = FakeScopes()
        val vm = build(fake, scopes = scopes)

        val state = vm.ui.value
        assertTrue("应进入圈层引导态", state.needsScopeSetup)
        assertTrue("未订阅时不该发任何取页请求", fake.pages.isEmpty())
    }

    /** 订阅第一个圈层：按该圈层取数，且默认激活它 */
    @Test
    fun subscribingAFandomFetchesInsideThatScope() {
        val fake = FakeSource().apply { items = listOf(work(1)) }
        val scopes = FakeScopes()
        val vm = build(fake, scopes = scopes)

        val hp = SourceScope("fandom", "Harry Potter - J. K. Rowling")
        vm.setScopes(listOf(hp))
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.ui.value
        assertFalse("有圈层后不再引导", state.needsScopeSetup)
        assertEquals(hp, state.activeScope)
        assertEquals(listOf(hp.id), fake.requestedScopes)
    }

    /** 切圈层：内容换了，必须用新圈层重新取（键不同，不会命中旧缓存） */
    @Test
    fun switchingScopeReloadsContentInsideTheNewScope() {
        val fake = FakeSource().apply { items = listOf(work(1)) }
        val scopes = FakeScopes()
        val vm = build(fake, scopes = scopes)

        val hp = SourceScope("fandom", "Harry Potter - J. K. Rowling")
        val st = SourceScope("fandom", "Stranger Things (TV 2016)")
        vm.setScopes(listOf(hp, st))
        dispatcher.scheduler.advanceUntilIdle()
        vm.selectScope(st)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(hp.id, st.id), fake.requestedScopes)
        assertEquals(st, vm.ui.value.activeScope)
    }

    /** 非圈层式源（没登记能力）：一切照旧，绝不进引导态 */
    @Test
    fun feedSourceWithoutScopesKeepsWorking() {
        val fake = FakeSource().apply { items = listOf(work(1)) }
        val vm = build(fake)
        assertFalse(vm.ui.value.needsScopeSetup)
        assertEquals("照常取第一页", listOf(Triple("popular", mapOf("type" to "all"), 0)), fake.pages)
        assertEquals("没有圈层能力就不带圈层", listOf<String?>(null), fake.requestedScopes)
    }
}
