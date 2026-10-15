package com.piku.client.core.ui.common

import com.piku.client.core.model.TagCard
import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkKind
import com.piku.client.core.model.WorkSource
import com.piku.client.core.model.key
import org.junit.Assert.assertEquals
import org.junit.Test

/** 分页追加去重：站点分页会飘（同一作品/标签落在相邻页），列表 item key 重复会让 Compose 直接抛异常 */
class PageMergeTest {

    private fun work(id: Long) = Work(
        id = id,
        authorId = 1L,
        authorName = "a",
        authorAvatarUrl = null,
        categoryCd = -1,
        categoryName = "",
        title = "t$id",
        thumbnailUrl = "",
        imageCount = 0,
        r18 = false,
        source = WorkSource.AO3,
        kind = WorkKind.NOVEL,
    )

    @Test
    fun appendedPageDropsWorksAlreadyInListAndKeepsOrder() {
        val current = listOf(work(1), work(2))
        // 第 2 篇在第 2 页又出现一次（实测崩溃就是这种重复）
        val page = listOf(work(2), work(3))

        val merged = mergePage(current, page) { w -> w.key }

        assertEquals(listOf(1L, 2L, 3L), merged.map { it.id })
    }

    @Test
    fun appendedPageCollapsesTagCardsWithSameName() {
        val current = listOf(TagCard(name = "Fluff"), TagCard(name = "Angst"))
        val page = listOf(TagCard(name = "Angst"), TagCard(name = "Hurt/Comfort"))

        val merged = mergePage(current, page) { c -> c.name }

        assertEquals(listOf("Fluff", "Angst", "Hurt/Comfort"), merged.map { it.name })
    }

    @Test
    fun sameIdOnDifferentSourcesStaysTwoEntries() {
        val ao3 = work(7)
        val poipiku = ao3.copy(source = WorkSource.POIPIKU, kind = WorkKind.ILLUST)

        val merged = mergePage(listOf(ao3), listOf(poipiku)) { w -> w.key }

        assertEquals(2, merged.size)
    }
}
