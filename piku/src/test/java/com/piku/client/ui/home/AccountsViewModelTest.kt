package com.piku.client.ui.home

import com.piku.client.R
import com.piku.client.data.local.InMemorySharedPreferences
import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.AuthStatus
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.ContentSource
import com.piku.client.domain.source.SourceAccount
import com.piku.client.domain.source.SourceAuth
import com.piku.client.domain.source.SourceAuthRegistry
import com.piku.client.domain.source.SourcePage
import com.piku.client.domain.source.SourceRegistry
import com.piku.client.domain.source.SourceWorkOpen
import com.piku.client.domain.source.SourceWorkPage
import com.piku.client.domain.usecase.ObserveHomeSourceUseCase
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

class AccountsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAuth(
        override val source: WorkSource,
        override val loginRoute: String? = null,
        override val logoutMessageRes: Int = R.string.account_logout_message,
        /** 该源有没有账号主页 */
        private val profile: String? = null,
        loggedIn: Boolean = false,
        account: SourceAccount? = null,
    ) : SourceAuth {
        private val _status = MutableStateFlow(
            if (loggedIn) AuthStatus.LOGGED_IN else AuthStatus.LOGGED_OUT,
        )
        override val status: StateFlow<AuthStatus> = _status.asStateFlow()

        private val _account = MutableStateFlow(account)
        override val account: StateFlow<SourceAccount?> = _account.asStateFlow()

        var logoutCalls = 0
            private set

        override fun profileId(account: SourceAccount): String? = profile

        fun login(account: SourceAccount) {
            _account.value = account
            _status.value = AuthStatus.LOGGED_IN
        }

        /** 已登录但资料还在路上 */
        fun loginWithoutProfile() {
            _status.value = AuthStatus.LOGGED_IN
        }

        override fun logout() {
            logoutCalls++
            _account.value = null
            _status.value = AuthStatus.LOGGED_OUT
        }
    }

    private class FakeContentSource(
        override val id: WorkSource,
        override val labelRes: Int,
    ) : ContentSource {
        override val feeds = emptyList<com.piku.client.domain.source.SourceFeed>()
        override val facets = emptyList<com.piku.client.domain.source.SourceFacetGroup>()

        override suspend fun page(
            feedId: String,
            facets: Map<String, String>,
            page: Int,
        ): Result<SourcePage> = Result.success(SourcePage(emptyList()))

        override fun open(work: Work): SourceWorkOpen = SourceWorkOpen.InAppViewer

        override suspend fun workPages(work: Work): Result<List<SourceWorkPage>> =
            Result.success(emptyList())
    }

    private fun labelFor(source: WorkSource): Int = when (source) {
        WorkSource.POIPIKU -> R.string.home_source_poipiku
        WorkSource.PIXIV -> R.string.home_source_pixiv
    }

    private fun build(
        vararg auths: FakeAuth,
        homeSource: WorkSource = WorkSource.POIPIKU,
        /** 有内容源、但没有登录插件的源（用来验证兜底行） */
        sourcesWithoutPlugin: List<WorkSource> = emptyList(),
    ): Pair<AccountsViewModel, SettingsRepository> {
        val settings = SettingsRepository(InMemorySharedPreferences())
        settings.setHomeSource(homeSource)
        val sources = (auths.map { it.source } + sourcesWithoutPlugin)
            .distinct()
            .map { FakeContentSource(it, labelFor(it)) }
        val viewModel = AccountsViewModel(
            authRegistry = SourceAuthRegistry(auths.toSet()),
            sourceRegistry = SourceRegistry(sources.toSet()),
            observeHomeSourceUseCase = ObserveHomeSourceUseCase(settings),
        )
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel to settings
    }

    private fun poipiku(loggedIn: Boolean = false, account: SourceAccount? = null) = FakeAuth(
        source = WorkSource.POIPIKU,
        loginRoute = "login",
        profile = "42",
        loggedIn = loggedIn,
        account = account,
    )

    private fun pixiv(loggedIn: Boolean = false, account: SourceAccount? = null) = FakeAuth(
        source = WorkSource.PIXIV,
        loginRoute = "pixiv_login",
        // pixiv 没有应用内主页 → 头部点击应落到账号页
        profile = null,
        loggedIn = loggedIn,
        account = account,
    )

    /** 一行一个源、顺序稳定；**行与行之间没有主次** */
    @Test
    fun oneRowPerSourceWithoutRanking() {
        val (viewModel, _) = build(pixiv(loggedIn = true, account = SourceAccount("ぴく")), poipiku())

        assertEquals(
            listOf(WorkSource.POIPIKU, WorkSource.PIXIV),
            viewModel.rows.value.map { it.source },
        )
    }

    @Test
    fun eachRowCarriesItsOwnCapabilities() {
        val (viewModel, _) = build(
            poipiku(loggedIn = true, account = SourceAccount("我", account = "42")),
            pixiv(loggedIn = true, account = SourceAccount("ぴく", account = "piku_user")),
        )

        val poipikuRow = viewModel.rows.value.first { it.source == WorkSource.POIPIKU }
        val pixivRow = viewModel.rows.value.first { it.source == WorkSource.PIXIV }

        assertEquals("login", poipikuRow.loginRoute)
        assertEquals("pixiv_login", pixivRow.loginRoute)
        // 有主页的源才给得出 profileId（外壳据此决定头部点哪里）
        assertEquals("42", poipikuRow.profileId)
        assertNull(pixivRow.profileId)
        assertEquals(poipikuRow.logoutMessageRes, pixivRow.logoutMessageRes)
    }

    @Test
    fun rowsCarryPerSourceState() {
        val (viewModel, _) = build(
            poipiku(loggedIn = true, account = SourceAccount(displayName = "我", account = "42")),
            pixiv(loggedIn = false),
        )

        val poipikuRow = viewModel.rows.value.first { it.source == WorkSource.POIPIKU }
        val pixivRow = viewModel.rows.value.first { it.source == WorkSource.PIXIV }

        assertEquals("42", poipikuRow.account?.account)
        assertTrue(poipikuRow.loggedIn)
        assertFalse(pixivRow.loggedIn)
    }

    /** 已登录但资料还没到：是"骨架"，不是"未登录"——两者文案完全不同 */
    @Test
    fun loggedInWithoutProfileIsPendingNotLoggedOut() {
        val auth = pixiv()
        val (viewModel, _) = build(poipiku(), auth)

        auth.loginWithoutProfile()
        dispatcher.scheduler.advanceUntilIdle()

        val row = viewModel.rows.value.first { it.source == WorkSource.PIXIV }
        assertTrue(row.loggedIn)
        assertTrue(row.pending)
    }

    /** 登出只落到被点名的那一个源：按源隔离，不能一登出把别的账号也带走 */
    @Test
    fun logoutOnlyTouchesTheNamedSource() {
        val pixivAuth = pixiv(loggedIn = true, account = SourceAccount("ぴく"))
        val poipikuAuth = poipiku(loggedIn = true, account = SourceAccount("我"))
        val (viewModel, _) = build(poipikuAuth, pixivAuth)

        viewModel.logout(WorkSource.PIXIV)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, pixivAuth.logoutCalls)
        assertEquals(0, poipikuAuth.logoutCalls)
        assertTrue(viewModel.rows.value.first { it.source == WorkSource.POIPIKU }.loggedIn)
    }

    /** 抽屉头部跟的是当前首页源：切源就换人 */
    @Test
    fun currentFollowsTheHomeSource() {
        val (viewModel, settings) = build(
            poipiku(loggedIn = true, account = SourceAccount("我")),
            pixiv(loggedIn = true, account = SourceAccount("ぴく")),
            homeSource = WorkSource.POIPIKU,
        )
        assertEquals(WorkSource.POIPIKU, viewModel.current.value?.source)

        settings.setHomeSource(WorkSource.PIXIV)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(WorkSource.PIXIV, viewModel.current.value?.source)
        assertEquals("ぴく", viewModel.current.value?.account?.displayName)
    }

    /** 该源还没接登录插件时也要给一行：头部不能永远停在骨架上 */
    @Test
    fun currentSourceWithoutPluginStillYieldsARow() {
        val (viewModel, _) = build(
            poipiku(loggedIn = true, account = SourceAccount("我")),
            homeSource = WorkSource.PIXIV,
            sourcesWithoutPlugin = listOf(WorkSource.PIXIV),
        )

        val row = viewModel.current.value
        assertEquals(WorkSource.PIXIV, row?.source)
        assertFalse("没有插件就是未登录，不能画成骨架", row!!.pending)
        assertFalse(row.loggedIn)
        assertNull(row.loginRoute)
        assertNull("未登录时不该给出主页", row.profileId)
        // 账号页里也不该出现这种源（它没有账号可管）
        assertEquals(listOf(WorkSource.POIPIKU), viewModel.rows.value.map { it.source })
    }
}
