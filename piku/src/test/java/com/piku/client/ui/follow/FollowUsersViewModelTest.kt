package com.piku.client.ui.follow

import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.model.FollowUserPage
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.SourceAuthorOpen
import com.piku.client.domain.source.SourceFollows
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

/**
 * 关注页状态机（与源无关）：登录门、翻页终点、关注/取关落账、会话变化重拉。
 * 全部走假 SourceFollows，断言的是 VM 自己的分支行为。
 */
class FollowUsersViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeFollows(
        loggedIn: Boolean = true,
    ) : SourceFollows {
        private val _sessionVersion = MutableStateFlow(0L)
        override val sessionVersion: StateFlow<Long> = _sessionVersion.asStateFlow()
        override val sourceId = WorkSource.POIPIKU
        override val loggedIn: Boolean = loggedIn

        val pages = mutableMapOf<Int, Result<FollowUserPage>>()
        val followsCalls = mutableListOf<Int>()
        val setFollowResults = mutableMapOf<Long, Result<Boolean>>()
        val setFollowCalls = mutableListOf<Pair<Long, Boolean>>()

        override suspend fun follows(page: Int): Result<FollowUserPage> {
            followsCalls += page
            return pages.getValue(page)
        }

        override suspend fun setFollowed(userId: Long, follow: Boolean): Result<Boolean> {
            setFollowCalls += userId to follow
            return setFollowResults.getValue(userId)
        }

        override fun userPage(user: FollowUser): SourceAuthorOpen = SourceAuthorOpen.NativeDetail

        fun bumpSession() {
            _sessionVersion.value += 1
        }
    }

    private fun user(id: Long) = FollowUser(userId = id, name = "u$id", avatarUrl = null)

    private fun page(vararg ids: Long, total: Int? = null) =
        FollowUserPage(users = ids.map(::user), total = total)

    private fun build(follows: FakeFollows): FollowUsersViewModel {
        val vm = object : FollowUsersViewModel(follows) {}
        dispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    @Test
    fun loggedOutShowsLoginGateWithoutFetching() {
        val follows = FakeFollows(loggedIn = false)
        val vm = build(follows)

        assertTrue(vm.uiState.value.followNeedLogin)
        assertTrue(vm.uiState.value.users.isEmpty())
        assertTrue(vm.uiState.value.endReached)
        // 未登录不该发请求
        assertTrue(follows.followsCalls.isEmpty())
    }

    @Test
    fun firstPagePopulatesUsersAndKeepsUnknownTotalOpen() {
        val follows = FakeFollows()
        follows.pages[0] = Result.success(page(1, 2))
        val vm = build(follows)

        val state = vm.uiState.value
        assertEquals(listOf(1L, 2L), state.users.map { it.userId })
        assertNull(state.total)
        // total 未知时不能拿页大小凑数判底
        assertFalse(state.endReached)
        assertFalse(state.followNeedLogin)
    }

    @Test
    fun unknownTotalPagingEndsOnEmptyPage() {
        val follows = FakeFollows()
        follows.pages[0] = Result.success(page(1, 2))
        follows.pages[1] = Result.success(page())
        val vm = build(follows)

        vm.loadMore()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.endReached)
        assertEquals(listOf(0, 1), follows.followsCalls)
    }

    @Test
    fun knownTotalEndsWhenListCoversIt() {
        val follows = FakeFollows()
        follows.pages[0] = Result.success(page(1, 2, total = 2))
        val vm = build(follows)

        assertTrue(vm.uiState.value.endReached)
        vm.loadMore()
        dispatcher.scheduler.advanceUntilIdle()
        // 到底后不再翻页
        assertEquals(listOf(0), follows.followsCalls)
    }

    @Test
    fun unfollowBooksActualServerState() {
        val follows = FakeFollows()
        follows.pages[0] = Result.success(page(1))
        follows.setFollowResults[1] = Result.success(false)
        val vm = build(follows)

        vm.toggleFollow(1)
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(1L in state.unfollowedIds)
        assertTrue(state.unfollowingIds.isEmpty())
        assertEquals(listOf(1L to false), follows.setFollowCalls)
    }

    @Test
    fun refollowRemovesFromUnfollowed() {
        val follows = FakeFollows()
        follows.pages[0] = Result.success(page(1))
        follows.setFollowResults[1] = Result.success(false)
        val vm = build(follows)
        vm.toggleFollow(1)
        dispatcher.scheduler.advanceUntilIdle()

        follows.setFollowResults[1] = Result.success(true)
        vm.toggleFollow(1)
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(1L in state.unfollowedIds)
        assertEquals(listOf(1L to false, 1L to true), follows.setFollowCalls)
    }

    @Test
    fun failedToggleKeepsRowStateAndClearsSending() {
        val follows = FakeFollows()
        follows.pages[0] = Result.success(page(1))
        follows.setFollowResults[1] = Result.failure(AppError.Unknown)
        val vm = build(follows)

        vm.toggleFollow(1)
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.unfollowingIds.isEmpty())
        assertFalse(1L in state.unfollowedIds)
    }

    @Test
    fun toggleWhileInFlightIsIgnored() {
        val follows = FakeFollows()
        follows.pages[0] = Result.success(page(1))
        follows.setFollowResults[1] = Result.success(false)
        val vm = build(follows)

        // 两次连点只落一笔：第一笔在途时第二次被挡掉
        vm.toggleFollow(1)
        vm.toggleFollow(1)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, follows.setFollowCalls.size)
    }

    @Test
    fun sessionChangeReloadsFromFirstPage() {
        val follows = FakeFollows()
        follows.pages[0] = Result.success(page(1))
        val vm = build(follows)

        follows.pages[0] = Result.success(page(1, 2))
        follows.bumpSession()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(1L, 2L), vm.uiState.value.users.map { it.userId })
        // 首次拉取 + 会话变化后的重拉
        assertEquals(listOf(0, 0), follows.followsCalls)
    }
}
