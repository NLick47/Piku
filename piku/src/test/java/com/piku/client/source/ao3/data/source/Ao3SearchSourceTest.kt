package com.piku.client.source.ao3.data.source

import com.piku.client.source.ao3.data.remote.Ao3Api
import com.piku.client.source.ao3.data.repository.Ao3Repository
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ao3SearchSourceTest {

    /** 记下入参的假 api，[html] 是标签搜索结果页 */
    private class FakeAo3Api(private val html: String) : Ao3Api {
        val tagCalls = mutableListOf<Pair<String?, String?>>()
        val tagWorkCalls = mutableListOf<Triple<String, Int, String?>>()

        override suspend fun searchTags(
            name: String?,
            type: String?,
            status: String,
            sortColumn: String,
            sortDirection: String,
        ): ResponseBody {
            tagCalls += name to type
            return html.toResponseBody("text/html".toMediaType())
        }

        override suspend fun getTagWorks(
            tag: String,
            page: Int,
            sortColumn: String?,
            sortDirection: String,
        ): ResponseBody {
            tagWorkCalls += Triple(tag, page, sortColumn)
            return html.toResponseBody("text/html".toMediaType())
        }

        override suspend fun getWork(id: Long, adult: Boolean): ResponseBody = error("检索不该走详情")

        override suspend fun getWorkFull(id: Long, fullWork: Boolean, adult: Boolean): ResponseBody =
            error("检索不该走全文")

        override suspend fun searchWorks(
            query: String?,
            scope: Map<String, String>,
            sortColumn: String?,
            sortDirection: String,
            page: Int,
        ): ResponseBody = error("检索不该走作品列表")

        override suspend fun getUserWorks(user: String, page: Int): ResponseBody = error("检索不该走用户作品")
    }

    private fun rows(vararg rows: Pair<String, Int>): String = buildString {
        append("<ol class=\"tag index group\">")
        rows.forEach { (name, count) ->
            append("<li><span class='canonical'>Fandom: <a class=\"tag\" href=\"/tags/x\">")
            append(name)
            append("</a> &lrm;(")
            append(count)
            append(")</span></li>")
        }
        append("</ol>")
    }

    private fun source(api: Ao3Api) = Ao3SearchSource(Ao3Repository(api))

    /** 联想不限类型：站点一次回四种标签混排，作品数要带给外壳 */
    @Test
    fun suggestSearchesEveryTagType() = runBlocking {
        val api = FakeAo3Api(rows("Harry Potter - J. K. Rowling" to 1025530, "Harry Potter" to 504466))
        val found = source(api).suggest("harry").getOrThrow()

        assertEquals(listOf("harry" to null), api.tagCalls)
        assertEquals(
            listOf("Harry Potter - J. K. Rowling" to 1025530, "Harry Potter" to 504466),
            found.map { it.name to it.count },
        )
    }

    /**
     * 热门榜名字留空、类型钉成同人；不钉类型时站点回的是警告分级那批标签，摆出来不是"热门同人"
     */
    @Test
    fun trendingTagsListsMostUsedFandoms() = runBlocking {
        val api = FakeAo3Api(rows("Harry Potter - J. K. Rowling" to 1025530, "Original Work" to 300000))
        val hot = source(api).trendingTags().getOrThrow()

        assertEquals(listOf(null to "Fandom"), api.tagCalls)
        assertEquals(listOf("Harry Potter - J. K. Rowling", "Original Work"), hot.map { it.name })
        assertEquals(1025530, hot.first().count)
        // 纯文字站没有代表作图：交给外壳画中性占位，不是塞一张假图
        assertTrue(hot.all { it.thumbnailUrl.isEmpty() })
    }

    /** 站点一页 50 条，待机态只取榜单前几 */
    @Test
    fun trendingTagsKeepsOnlyTheTopOfTheList() = runBlocking {
        val api = FakeAo3Api(rows(*(1..50).map { "Fandom $it" to 1000 - it }.toTypedArray()))
        val hot = source(api).trendingTags().getOrThrow()

        assertEquals(Ao3SearchSource.HOT_FANDOM_LIMIT, hot.size)
        assertEquals("Fandom 1", hot.first().name)
        assertEquals("Fandom ${Ao3SearchSource.HOT_FANDOM_LIMIT}", hot.last().name)
    }

    /** 缺作品数的行也要能进榜，数不是入选条件 */
    @Test
    fun trendingTagsTolerateMissingCount() = runBlocking {
        val api = FakeAo3Api(
            "<ol class=\"tag index group\"><li><span class='canonical'>Fandom: " +
                "<a class=\"tag\" href=\"/tags/x\">Some Fandom</a></span></li></ol>",
        )
        val hot = source(api).trendingTags().getOrThrow()

        assertEquals(listOf("Some Fandom"), hot.map { it.name })
        assertNull(hot.first().count)
    }

    /** 选了排序就透传，最佳匹配留空——标签页上不带参数才是站点默认序 */
    @Test
    fun tagWorksForwardThePickedSortAndLeaveTheDefaultToTheSite() = runBlocking {
        val picked = FakeAo3Api(rows())
        source(picked).searchTagWorks(
            "Fluff",
            mapOf(Ao3SearchSource.FILTER_SORT to Ao3SearchSource.SORT_TOP),
            page = 1,
        )

        val untouched = FakeAo3Api(rows())
        source(untouched).searchTagWorks("Fluff", emptyMap(), page = 0)

        assertEquals(
            listOf(Triple("Fluff", 2, Ao3SearchSource.SORT_TOP)),
            picked.tagWorkCalls,
        )
        assertEquals(listOf(Triple("Fluff", 1, null)), untouched.tagWorkCalls)
    }
}
