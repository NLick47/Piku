package com.piku.client.data.remote.pixiv

import com.piku.client.data.remote.PikuJson
import kotlinx.serialization.decodeFromString
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
}
