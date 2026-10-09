package com.piku.client.ui.history

import com.piku.client.domain.model.HistoryItem
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkKey
import com.piku.client.domain.model.WorkSource
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryOrderFreezeTest {

    private fun entry(id: Long, visitedAt: Long) = HistoryItem(work = work(id), visitedAt = visitedAt)

    private fun work(id: Long) = Work(
        id = id,
        authorId = 1L,
        authorName = "作者",
        authorAvatarUrl = null,
        categoryCd = -1,
        categoryName = "",
        title = "作品$id",
        thumbnailUrl = "https://x/$id.jpg",
        imageCount = 1,
        r18 = false,
        source = WorkSource.POIPIKU,
    )

    @Test
    fun revisitInsidePageKeepsPositionAndFrozenTime() {
        val freeze = HistoryOrderFreeze()
        freeze.freeze(listOf(entry(1, 300), entry(2, 200), entry(3, 100)))

        // 页内点开作品 3：记录被改写为最新浏览，列表顺序与展示时间都不该动
        val after = freeze.freeze(listOf(entry(1, 300), entry(2, 200), entry(3, 999)))

        assertEquals(listOf(1L, 2L, 3L), after.map { it.work.id })
        assertEquals("页内不重排，时间留在进入时", 100L, after.last().visitedAt)
    }

    @Test
    fun newVisitAppearsOnTop() {
        val freeze = HistoryOrderFreeze()
        freeze.freeze(listOf(entry(1, 300), entry(2, 200)))

        val after = freeze.freeze(listOf(entry(9, 500), entry(1, 300), entry(2, 200)))

        assertEquals(listOf(9L, 1L, 2L), after.map { it.work.id })
    }

    @Test
    fun filterChangeOrReentryRereadsLatestOrder() {
        val freeze = HistoryOrderFreeze()
        freeze.freeze(listOf(entry(1, 300), entry(2, 200)))
        freeze.reset()

        val after = freeze.freeze(listOf(entry(2, 400), entry(1, 300)))

        assertEquals(listOf(2L, 1L), after.map { it.work.id })
    }

    @Test
    fun latestTimeKeepsRealVisitTimeForUndo() {
        val freeze = HistoryOrderFreeze()
        freeze.freeze(listOf(entry(1, 300), entry(2, 200)))
        freeze.freeze(listOf(entry(1, 300), entry(2, 500)))

        assertEquals(
            "撤销要写回真实浏览时间，不能写冻结时间",
            500L,
            freeze.latestTime(WorkKey(WorkSource.POIPIKU, "2")),
        )
    }
}
