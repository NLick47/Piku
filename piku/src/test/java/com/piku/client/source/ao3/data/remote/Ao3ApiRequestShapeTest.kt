package com.piku.client.source.ao3.data.remote

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class Ao3ApiRequestShapeTest {

    private fun serveRaw(path: String, capture: AtomicReference<String>): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext(path) { exchange ->
            capture.set(exchange.requestURI.toString())
            val body = "<html>ok</html>".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        return server
    }

    private fun api(port: Int): Ao3Api = Retrofit.Builder()
        .baseUrl("http://127.0.0.1:$port/")
        .build()
        .create(Ao3Api::class.java)

    @Test
    fun listingCarriesSortColumnAndDirection() {
        val captured = AtomicReference("")
        val server = serveRaw("/works/search", captured)
        try {
            runBlocking {
                api(server.address.port)
                    .searchWorks(query = null, sortColumn = "kudos_count")
                    .string()
            }
            val uri = captured.get()
            assertTrue("排序字段没发出去: $uri", uri.contains("work_search%5Bsort_column%5D=kudos_count"))
            assertTrue("排序方向没发出去: $uri", uri.contains("work_search%5Bsort_direction%5D=desc"))
            assertTrue("默认带第一页: $uri", uri.contains("page=1"))
            assertTrue("无查询词时不该出现 query 参数: $uri", !uri.contains("query="))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun listingPageIsPassedThrough() {
        val captured = AtomicReference("")
        val server = serveRaw("/works/search", captured)
        try {
            runBlocking {
                api(server.address.port)
                    .searchWorks(query = null, sortColumn = "created_at", page = 3)
                    .string()
            }
            assertTrue("翻页参数没发出去: ${captured.get()}", captured.get().contains("page=3"))
            assertTrue(captured.get().contains("work_search%5Bsort_column%5D=created_at"))
        } finally {
            server.stop(0)
        }
    }

    /** 全文一次拉：view_full_work=true 必须在请求里，否则只能读到第一章 */
    @Test
    fun fullWorkFlagIsSentWhenReading() {
        val captured = AtomicReference("")
        val server = serveRaw("/works/20251606", captured)
        try {
            runBlocking {
                api(server.address.port).getWorkFull(20251606L).string()
            }
            assertTrue("全文参数没发出去: ${captured.get()}", captured.get().contains("view_full_work=true"))
        } finally {
            server.stop(0)
        }
    }

    /**
     * 详情页不拉全文。咬人：布尔 Query 在 false 时也会拼进 URL，
     * 而 AO3 按参数有无判断（`view_full_work=false` 一样回全文），
     * 所以详情必须走不带该参数的重载——这条挡的是"详情悄悄拉 400KB"。
     */
    @Test
    fun detailRequestOmitsFullWorkFlag() {
        val captured = AtomicReference("")
        val server = serveRaw("/works/20251606", captured)
        try {
            runBlocking {
                api(server.address.port).getWork(20251606L).string()
            }
            assertFalse("详情不该带全文参数: ${captured.get()}", captured.get().contains("view_full_work"))
        } finally {
            server.stop(0)
        }
    }

    /**
     * 两个详情端都要带 `view_adult=true`。咬人：不带这个参数时 R-18 作品回的是
     * 站点那张成人内容警告页（HTTP 200，没有正文与元数据），解析器只会报 NotFound——
     * 线上表现为"R-18 作品点不开"。
     */
    @Test
    fun workRequestsAcceptTheAdultGate() {
        val detail = AtomicReference("")
        val detailServer = serveRaw("/works/20251606", detail)
        try {
            runBlocking { api(detailServer.address.port).getWork(20251606L).string() }
            assertTrue("详情没接受成人门: ${detail.get()}", detail.get().contains("view_adult=true"))
        } finally {
            detailServer.stop(0)
        }

        val full = AtomicReference("")
        val fullServer = serveRaw("/works/20251606", full)
        try {
            runBlocking { api(fullServer.address.port).getWorkFull(20251606L).string() }
            assertTrue("全文没接受成人门: ${full.get()}", full.get().contains("view_adult=true"))
        } finally {
            fullServer.stop(0)
        }
    }

    /**
     * 联想走标签搜索。参数名写错不会有任何报错：AO3 把不认识的参数当没写，
     * 结果会静默变成"按名字母序的全状态标签"，联想质量静悄悄地垮掉。
     */
    @Test
    fun tagSearchCarriesNameTypeAndSort() {
        val captured = AtomicReference("")
        val server = serveRaw("/tags/search", captured)
        try {
            runBlocking {
                api(server.address.port)
                    .searchTags(name = "harry potter", type = "Fandom")
                    .string()
            }
            val uri = captured.get()
            assertTrue("搜索词没发出去: $uri", uri.contains("tag_search%5Bname%5D=harry%20potter"))
            assertTrue("类型没发出去: $uri", uri.contains("tag_search%5Btype%5D=Fandom"))
            assertTrue("没限定规范标签: $uri", uri.contains("tag_search%5Bwrangling_status%5D=canonical"))
            assertTrue("没按使用量排序: $uri", uri.contains("tag_search%5Bsort_column%5D=uses"))
            assertTrue("排序方向没发出去: $uri", uri.contains("tag_search%5Bsort_direction%5D=desc"))
        } finally {
            server.stop(0)
        }
    }

    /** 不带类型 = 跨全部类型检索；钉死某一个类型会凭空少一半结果 */
    @Test
    fun tagSearchAcrossAllTypesOmitsTheTypeParameter() {
        val captured = AtomicReference("")
        val server = serveRaw("/tags/search", captured)
        try {
            runBlocking {
                api(server.address.port).searchTags(name = "harry", type = null).string()
            }
            val uri = captured.get()
            assertTrue("搜索词没发出去: $uri", uri.contains("tag_search%5Bname%5D=harry"))
            assertFalse("类型该缺省成不限: $uri", uri.contains("tag_search%5Btype%5D"))
            assertTrue("没限定规范标签: $uri", uri.contains("tag_search%5Bwrangling_status%5D=canonical"))
        } finally {
            server.stop(0)
        }
    }

    /** 热门榜要钉成同人且留空名字；不带类型时站点回的是警告分级那批标签 */
    @Test
    fun hotTagListPinsFandomAndOmitsName() {
        val captured = AtomicReference("")
        val server = serveRaw("/tags/search", captured)
        try {
            runBlocking {
                api(server.address.port).searchTags(name = null, type = "Fandom").string()
            }
            val uri = captured.get()
            assertTrue("类型没钉成同人: $uri", uri.contains("tag_search%5Btype%5D=Fandom"))
            assertTrue("没按使用量排序: $uri", uri.contains("tag_search%5Bsort_column%5D=uses"))
            assertFalse("空名不该发出去: $uri", uri.contains("tag_search%5Bname%5D"))
        } finally {
            server.stop(0)
        }
    }

    /** 标签/用户列表路径（编码后的标签名要原样进 path） */
    @Test
    fun tagWorksPathIsPreserved() {
        val captured = AtomicReference("")
        val server = serveRaw("/tags/", captured)
        try {
            runBlocking {
                val body: ResponseBody = api(server.address.port).getTagWorks(tag = "Yuri!!! on Ice (Anime)", page = 2)
                body.string()
            }
            val uri = captured.get()
            assertTrue("标签名应保留在 path 里: $uri", uri.startsWith("/tags/"))
            assertTrue("翻页参数没发出去: $uri", uri.contains("page=2"))
        } finally {
            server.stop(0)
        }
    }
}
