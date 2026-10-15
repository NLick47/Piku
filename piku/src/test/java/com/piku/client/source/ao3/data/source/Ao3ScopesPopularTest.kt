package com.piku.client.source.ao3.data.source

import com.piku.client.core.storage.prefs.InMemorySharedPreferences
import com.piku.client.source.ao3.data.local.Ao3ScopeStore
import com.piku.client.source.ao3.data.remote.Ao3Api
import com.piku.client.source.ao3.data.repository.Ao3Repository
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class Ao3ScopesPopularTest {

    /** 记下入参的假 api，[html] 是标签搜索结果页 */
    private class FakeAo3Api(private val html: String) : Ao3Api {
        val tagCalls = mutableListOf<Pair<String?, String?>>()

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

        override suspend fun getWork(id: Long, adult: Boolean): ResponseBody = error("榜单不该走详情")

        override suspend fun getWorkFull(id: Long, fullWork: Boolean, adult: Boolean): ResponseBody =
            error("榜单不该走全文")

        override suspend fun searchWorks(
            query: String?,
            scope: Map<String, String>,
            sortColumn: String?,
            sortDirection: String,
            page: Int,
        ): ResponseBody = error("榜单不该走作品列表")

        override suspend fun getTagWorks(
            tag: String,
            page: Int,
            sortColumn: String?,
            sortDirection: String,
        ): ResponseBody = error("榜单不该走标签作品")

        override suspend fun getUserWorks(user: String, page: Int): ResponseBody = error("榜单不该走用户作品")
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

    private fun scopes(api: Ao3Api) = Ao3Scopes(
        repository = Ao3Repository(api),
        store = Ao3ScopeStore(InMemorySharedPreferences()),
    )

    /** 热门榜就是空名字 + 该维度的类型：同人榜回作品出处，配对榜回 A/B */
    @Test
    fun popularAsksForTheKindsOwnTypeWithoutName() = runBlocking {
        val api = FakeAo3Api(rows("Draco Malfoy/Harry Potter" to 186475))
        val found = scopes(api).popular(Ao3Scopes.KIND_RELATIONSHIP).getOrThrow()

        assertEquals(listOf(null to "Relationship"), api.tagCalls)
        assertEquals(listOf("Draco Malfoy/Harry Potter" to 186475), found.map { it.name to it.count })
    }

    /** 三个维度各自打各自的类型，别都落回同人 */
    @Test
    fun eachKindMapsToItsOwnTagType() = runBlocking {
        val fandom = FakeAo3Api(rows("Harry Potter - J. K. Rowling" to 1025530))
        scopes(fandom).popular(Ao3Scopes.KIND_FANDOM)
        val freeform = FakeAo3Api(rows("Fluff" to 3037361))
        scopes(freeform).popular(Ao3Scopes.KIND_FREEFORM)

        assertEquals(listOf(null to "Fandom"), fandom.tagCalls)
        assertEquals(listOf(null to "Freeform"), freeform.tagCalls)
    }

    /** 站点一页 50 条，这一页只摆够翻两屏的量 */
    @Test
    fun popularKeepsOnlyTheTopOfTheList() = runBlocking {
        val api = FakeAo3Api(rows(*(1..50).map { "Fandom $it" to 1000 - it }.toTypedArray()))
        val found = scopes(api).popular(Ao3Scopes.KIND_FANDOM).getOrThrow()

        assertEquals(Ao3Scopes.POPULAR_LIMIT, found.size)
        assertEquals("Fandom 1", found.first().name)
    }

    /** 不认识的维度不发请求：宁可不给榜，也别拿同人榜冒充别的维度 */
    @Test
    fun unknownKindSendsNoRequest() = runBlocking {
        val api = FakeAo3Api(rows("Harry Potter - J. K. Rowling" to 1))
        val found = scopes(api).popular("character").getOrThrow()

        assertEquals(emptyList<Pair<String?, String?>>(), api.tagCalls)
        assertEquals(emptyList<String>(), found.map { it.name })
    }
}
