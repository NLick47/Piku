package com.piku.client.data.remote.pixiv

import com.piku.client.data.remote.PikuJson
import com.piku.client.data.repository.toWorkStats
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivIllustResponseTest {

    private fun payload(): String =
        javaClass.getResourceAsStream("/pixiv/illust-150105774.json")!!.readBytes().decodeToString()

    @Test
    fun realIllustDetailPayloadParses() {
        val response = PikuJson.decodeFromString<PixivIllustResponse>(payload())

        assertTrue(!response.error)
        assertTrue(response.body.description.contains("発売"))
        assertTrue(response.body.tags.tags.any { it.tag == "漫画" })
    }

    /** 详情页那几个计数与元信息同一个接口就带出来了，按真实载荷逐项钉住 */
    @Test
    fun realIllustDetailPayloadCarriesStats() {
        val stats = PikuJson.decodeFromString<PixivIllustResponse>(payload()).body.toWorkStats()

        assertEquals(217599, stats.views)
        assertEquals(21134, stats.likes)
        assertEquals(22079, stats.bookmarks)
        assertEquals(691, stats.comments)
        assertEquals(29, stats.pageCount)
        assertEquals(2129, stats.width)
        assertEquals(3000, stats.height)
        assertEquals("2026-09-25", stats.postedAt.substring(0, 10))
        assertEquals("aokawa_7", stats.authorAccount)
    }

}
