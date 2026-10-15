package com.piku.client.core.ui.feature.tags

import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkSource
import com.piku.client.core.source.SourcePage
import com.piku.client.core.source.SourceSearch
import com.piku.client.core.source.SourceSearchRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagFeedSourceTest {

    private class FakeSearch(override val sourceId: WorkSource) : SourceSearch {
        val tagCalls = mutableListOf<Triple<String, Map<String, String>, Int>>()
        var result: Result<SourcePage> = Result.success(SourcePage(emptyList()))

        override suspend fun searchWorks(
            query: String,
            filters: Map<String, String>,
            page: Int,
        ): Result<SourcePage> = result

        override suspend fun searchTagWorks(
            tag: String,
            filters: Map<String, String>,
            page: Int,
        ): Result<SourcePage> {
            tagCalls += Triple(tag, filters, page)
            return result
        }
    }

    private fun ao3Work(id: Long) = Work(
        id = id,
        authorId = 7L,
        authorName = "作者",
        authorAvatarUrl = null,
        categoryCd = -1,
        categoryName = "",
        title = "作品",
        thumbnailUrl = "",
        imageCount = 0,
        r18 = false,
        source = WorkSource.AO3,
    )

    private fun build(
        search: FakeSearch,
        onFallback: (String, Int) -> Unit = { _, _ -> },
    ) = TagFeedSource(
        searchRegistry = SourceSearchRegistry(setOf(search)),
        poipikuFallback = { tag, page ->
            onFallback(tag, page)
            Result.success(emptyList())
        },
    )

    /** AO3 点标签不能再出 poipiku 的作品：本源的标签检索先接住 */
    @Test
    fun sourceWithPluginLoadsThroughItsOwnTagSearch() = runBlocking {
        val search = FakeSearch(WorkSource.AO3)
        search.result = Result.success(SourcePage(items = listOf(ao3Work(1L))))
        var fallbackCalls = 0
        val feed = build(search) { _, _ -> fallbackCalls++ }

        val works = feed.works(WorkSource.AO3, tag = "Fluff", page = 2).getOrThrow()

        assertEquals(listOf(1L), works.map { it.id })
        assertEquals(listOf(Triple("Fluff", emptyMap<String, String>(), 2)), search.tagCalls)
        assertEquals("有插件的源不该碰 poipiku 链路", 0, fallbackCalls)
    }

    /** poipiku 是唯一允许落到内置链路的源（它的检索还没抽成插件） */
    @Test
    fun unregisteredPoipikuFallsBackToBuiltInChain() = runBlocking {
        val search = FakeSearch(WorkSource.AO3)
        val fallbackCalls = mutableListOf<Pair<String, Int>>()
        val feed = build(search) { tag, page -> fallbackCalls += tag to page }

        feed.works(WorkSource.POIPIKU, tag = "百合", page = 0)

        assertEquals(listOf("百合" to 0), fallbackCalls)
        assertTrue("不该去问别的源的插件", search.tagCalls.isEmpty())
    }

    /** 未登记的源（新源忘了注册插件）：明确"没有内容"，绝不静默拿 poipiku 的作品顶替 */
    @Test
    fun unregisteredSourceRefusesInsteadOfBorrowingPoipikuData() = runBlocking {
        val search = FakeSearch(WorkSource.PIXIV)
        var fallbackCalls = 0
        val feed = build(search) { _, _ -> fallbackCalls++ }

        val result = feed.works(WorkSource.AO3, tag = "Fluff", page = 0)

        assertTrue("未登记的源必须失败", result.isFailure)
        assertEquals("不该碰内置链路", 0, fallbackCalls)
        assertTrue("也不该去问别的源的插件", search.tagCalls.isEmpty())
    }
}
