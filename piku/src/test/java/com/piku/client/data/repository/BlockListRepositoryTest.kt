package com.piku.client.data.repository

import com.piku.client.domain.model.FollowUser
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockListRepositoryTest {

    private val repo = BlockListRepository()

    private fun user(id: Long, name: String = "u$id", avatar: String? = "a$id") =
        FollowUser(userId = id, name = name, avatarUrl = avatar)

    @Test
    fun addAndRemoveKeepIdsAndEntriesInSync() {
        repo.add(11L, "alice", "a11")

        assertEquals(setOf(11L), repo.blockedIds.value)
        assertEquals(listOf(BlockListRepository.Entry(11L, "alice", "a11")), repo.entries.value)

        repo.remove(11L)

        assertTrue(repo.blockedIds.value.isEmpty())
        assertTrue(repo.entries.value.isEmpty())
    }

    @Test
    fun addMovesExistingEntryToEnd() {
        repo.add(1L, "a", null)
        repo.add(2L, "b", null)
        repo.add(1L, "a2", "avatar")

        assertEquals(listOf(2L, 1L), repo.entries.value.map { it.userId })
        assertEquals("a2", repo.entries.value.last().name)
    }

    @Test
    fun mergeFromServerAddsUsersAndFillsMissingFields() {
        repo.add(1L, "local", null)

        repo.mergeFromServer(listOf(user(1L, name = "", avatar = "server-avatar"), user(2L)))

        assertEquals(setOf(1L, 2L), repo.blockedIds.value)
        // 服务端缺昵称时保留本地值，头像以服务端为准
        assertEquals(BlockListRepository.Entry(1L, "local", "server-avatar"), repo.entries.value.first())
    }

    @Test
    fun mergeFromServerDoesNotResurrectLocallyUnblockedUser() {
        repo.mergeFromServer(listOf(user(1L), user(2L)))
        repo.remove(1L)

        // 服务端有缓存延迟，解除后拉到的分页里可能还带着这个人
        repo.mergeFromServer(listOf(user(1L), user(2L)))

        assertEquals(setOf(2L), repo.blockedIds.value)
    }

    @Test
    fun addClearsTombstone() {
        repo.remove(1L)

        repo.add(1L, "again", null)

        assertEquals(setOf(1L), repo.blockedIds.value)
    }

    @Test
    fun emptyServerPageLeavesExistingEntriesUntouched() {
        repo.mergeFromServer(listOf(user(1L)))

        // 拉到空页只表示"没有更多了"（分页终点），不能当成分页结果为空而清空名单
        repo.mergeFromServer(emptyList())

        assertEquals(setOf(1L), repo.blockedIds.value)
    }

    @Test
    fun mergeReEmitsOnlyWhenSomethingChanged() = runTest {
        repo.mergeFromServer(listOf(user(1L)))
        val emissions = mutableListOf<List<BlockListRepository.Entry>>()
        val job = launch { repo.entries.collect { emissions += it } }
        advanceUntilIdle()

        // 同内容合并不重复发射，否则每次翻页都会重排列表、重放首页过滤
        repo.mergeFromServer(listOf(user(1L)))
        advanceUntilIdle()
        assertEquals(1, emissions.size)

        repo.mergeFromServer(listOf(user(2L)))
        advanceUntilIdle()
        assertEquals(2, emissions.size)
        job.cancel()
    }

    @Test
    fun clearEmptiesListAndTombstones() {
        repo.mergeFromServer(listOf(user(1L)))
        repo.remove(2L)

        repo.clear()

        assertTrue(repo.blockedIds.value.isEmpty())
        assertTrue(repo.entries.value.isEmpty())
        // tombstones 也一并清空：换账号后同名 ID 不该被上一条记录挡住
        repo.mergeFromServer(listOf(user(2L)))
        assertEquals(setOf(2L), repo.blockedIds.value)
    }
}
