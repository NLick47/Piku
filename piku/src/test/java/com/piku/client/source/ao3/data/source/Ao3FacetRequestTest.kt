package com.piku.client.source.ao3.data.source

import com.piku.client.core.source.SourceScope
import com.piku.client.source.ao3.data.remote.Ao3Api
import com.piku.client.source.ao3.data.remote.Ao3FacetParams
import com.piku.client.source.ao3.data.remote.Ao3ScopeParams
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import java.net.InetSocketAddress
import java.net.URLDecoder

class Ao3FacetRequestTest {

    private val captured = mutableListOf<String>()

    private fun server(): HttpServer {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/works/search") { exchange ->
            captured += exchange.requestURI.toString()
            val body = "<html>ok</html>".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        http.start()
        return http
    }

    private fun api(port: Int): Ao3Api = Retrofit.Builder()
        .baseUrl("http://127.0.0.1:$port/")
        .build()
        .create(Ao3Api::class.java)

    private fun param(query: String, name: String): String? {
        val key = "work_search%5B$name%5D="
        return query.split("&")
            .firstOrNull { it.startsWith(key) }
            ?.removePrefix(key)
            ?.let { URLDecoder.decode(it, "UTF-8") }
    }

    private fun run(vararg choices: Pair<String, String>): String {
        val http = server()
        return try {
            runBlocking {
                api(http.address.port).searchWorks(
                    query = null,
                    scope = Ao3FacetParams.of(mapOf(*choices)),
                    sortColumn = "revised_at",
                ).string()
            }
            captured.last().substringAfter("?")
        } finally {
            http.stop(0)
        }
    }

    @Test
    fun completeFilterReachesTheQueryString() {
        val query = run(Ao3ContentSource.FACET_COMPLETE to Ao3ContentSource.COMPLETE_YES)
        assertEquals("T", param(query, "complete"))
    }

    @Test
    fun inProgressFilterReachesTheQueryString() {
        val query = run(Ao3ContentSource.FACET_COMPLETE to Ao3ContentSource.COMPLETE_NO)
        assertEquals("F", param(query, "complete"))
    }

    /** 圈层与筛选同源同批：两组参数必须同时出现，互不覆盖 */
    @Test
    fun scopeAndFilterCoexistInOneQueryString() {
        val scope = SourceScope(Ao3Scopes.KIND_FANDOM, "Harry Potter - J. K. Rowling")
        val http = server()
        val query = try {
            runBlocking {
                api(http.address.port).searchWorks(
                    query = null,
                    scope = Ao3ScopeParams.of(scope) + Ao3FacetParams.of(
                        mapOf(
                            Ao3ContentSource.FACET_COMPLETE to Ao3ContentSource.COMPLETE_NO,
                        ),
                    ),
                    sortColumn = "revised_at",
                ).string()
            }
            captured.last().substringAfter("?")
        } finally {
            http.stop(0)
        }

        assertEquals("Harry Potter - J. K. Rowling", param(query, "fandom_names"))
        assertEquals("F", param(query, "complete"))
        assertTrue("排序仍要带: $query", query.contains("work_search%5Bsort_column%5D=revised_at"))
    }

    /** 不收窄时不发多余参数：缺省即"全部"，发空值反而会把结果筛没 */
    @Test
    fun defaultFiltersEmitNoExtraParams() {
        val query = run(Ao3ContentSource.FACET_COMPLETE to Ao3ContentSource.COMPLETE_ALL)
        assertTrue("默认完结态不该发参数: $query", !query.contains("complete"))
    }
}
