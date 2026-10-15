package com.piku.client.source.ao3.data.repository

import com.piku.client.source.ao3.data.remote.Ao3Api
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class Ao3TagListingRequestTest {

    private fun serve(captured: AtomicReference<String>): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/tags/") { exchange ->
            captured.set(exchange.requestURI.toString())
            val body = "<ol class=\"work index group\"></ol>".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        return server
    }

    private fun listing(tag: String, sortColumn: String?): String {
        val captured = AtomicReference("")
        val server = serve(captured)
        return try {
            val api = Retrofit.Builder()
                .baseUrl("http://127.0.0.1:${server.address.port}/")
                .build()
                .create(Ao3Api::class.java)
            runBlocking {
                Ao3Repository(api).listing(
                    query = null,
                    tag = tag,
                    user = null,
                    sortColumn = sortColumn,
                    page = 1,
                ).getOrThrow()
            }
            captured.get()
        } finally {
            server.stop(0)
        }
    }

    /** 热门榜与联想里带点号的标签不能死在 404 上 */
    @Test
    fun tagNameIsEscapedToTheSitePathForm() {
        val uri = listing("Harry Potter - J. K. Rowling", sortColumn = null)

        assertTrue("点号没转成站点的转义形态: $uri", uri.contains("J*d*%20K*d*%20Rowling"))
        assertFalse("原样点号会 404: $uri", uri.contains("J.%20K."))
    }

    /** 配对标签的斜杠同理：`*s*` 才是站点的写法 */
    @Test
    fun slashesAndAmpersandsAreEscaped() {
        assertTrue(listing("Draco Malfoy/Harry Potter", null).contains("Draco%20Malfoy*s*Harry%20Potter"))
        assertTrue(listing("A & B", null).contains("A%20*a*%20B"))
    }

    /** 选了排序就得让标签页真按它排，别是假控件 */
    @Test
    fun sortIsForwardedToTheTagPage() {
        val uri = listing("Fluff", sortColumn = "revised_at")

        assertTrue("排序没透传: $uri", uri.contains("work_search%5Bsort_column%5D=revised_at"))
        assertTrue("排序方向没发出去: $uri", uri.contains("work_search%5Bsort_direction%5D=desc"))
    }

    /** null = 不带排序参数，站点按默认排 */
    @Test
    fun missingSortSendsNoSortParameter() {
        val uri = listing("Fluff", sortColumn = null)

        assertFalse("默认排序不该拼 _score: $uri", uri.contains("sort_column"))
    }
}
