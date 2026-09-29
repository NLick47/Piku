package com.piku.client.data.remote.pixiv

import com.piku.client.data.remote.PikuJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivPagesResponseTest {

    private fun payload(): String =
        javaClass.getResourceAsStream("/pixiv/pages-150105774.json")!!.readBytes().decodeToString()

    @Test
    fun realPagesPayloadParsesWithPerPageUrls() {
        val response = PikuJson.decodeFromString<PixivPagesResponse>(payload())

        assertTrue(!response.error)
        assertEquals(29, response.body.size)
        assertEquals(
            "https://i.pximg.net/img-master/img/2026/09/26/00/05/02/150105774_p0_master1200.jpg",
            response.body[0].urls.regular,
        )
        assertEquals(
            "https://i.pximg.net/img-master/img/2026/09/26/00/05/02/150105774_p28_master1200.jpg",
            response.body[28].urls.regular,
        )
        assertTrue(response.body[0].width > 0)
    }
}
