package com.piku.client.core.ui.novelcache

import com.piku.client.core.model.WorkKey
import com.piku.client.core.model.WorkSource
import com.piku.client.core.novelcache.NovelCacheSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class NovelCacheListOpsTest {

    private fun item(
        source: WorkSource,
        id: Long,
        title: String,
        author: String = "作者",
        size: Long = 100,
        fetchedAt: Long = 0,
    ) = NovelCacheItem(
        summary = NovelCacheSummary(
            key = WorkKey(source, "w$id"),
            title = title,
            authorName = author,
            authorId = 1,
            chapterCount = 1,
            byteSize = size,
            fetchedAt = fetchedAt,
        ),
        progressPercent = 0,
    )

    @Test
    fun sourceFilterKeepsOnlyThatSource() {
        val items = listOf(
            item(WorkSource.AO3, 1, "a"),
            item(WorkSource.PIXIV, 2, "b"),
            item(WorkSource.AO3, 3, "c"),
        )

        val filtered = NovelCacheListOps.filter(items, WorkSource.AO3, query = "")

        assertEquals(listOf("a", "c"), filtered.map { it.summary.title })
        assertEquals(items, NovelCacheListOps.filter(items, null, query = ""))
    }

    @Test
    fun queryMatchesTitleOrAuthorIgnoringCase() {
        val items = listOf(
            item(WorkSource.AO3, 1, "Night Sea"),
            item(WorkSource.PIXIV, 2, "夏天的风", author = "Yuki"),
            item(WorkSource.POIPIKU, 3, "黄昏"),
        )

        assertEquals(listOf("Night Sea"), NovelCacheListOps.filter(items, null, "night").map { it.summary.title })
        assertEquals(listOf("夏天的风"), NovelCacheListOps.filter(items, null, "yuki").map { it.summary.title })
        assertEquals("查询两侧的空白应被忽略", listOf("黄昏"), NovelCacheListOps.filter(items, null, " 黄昏 ").map { it.summary.title })
        assertEquals(0, NovelCacheListOps.filter(items, null, "不存在的词").size)
    }

    @Test
    fun sortsByTitleSizeAndRecent() {
        val items = listOf(
            item(WorkSource.AO3, 1, "c", size = 300, fetchedAt = 100),
            item(WorkSource.AO3, 2, "a", size = 200, fetchedAt = 300),
            item(WorkSource.AO3, 3, "b", size = 900, fetchedAt = 200),
        )

        assertEquals(
            listOf("a", "b", "c"),
            NovelCacheListOps.sort(items, NovelCacheSort.TITLE).map { it.summary.title },
        )
        assertEquals(
            listOf("b", "c", "a"),
            NovelCacheListOps.sort(items, NovelCacheSort.SIZE).map { it.summary.title },
        )
        assertEquals(
            listOf("a", "b", "c"),
            NovelCacheListOps.sort(items, NovelCacheSort.RECENT).map { it.summary.title },
        )
    }
}
