package com.piku.client.data.remote.pixiv

import com.piku.client.data.remote.PikuJson
import com.piku.client.data.repository.toWork
import com.piku.client.domain.model.WorkSource
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivRecommendResponseTest {

    private fun payload(): String =
        javaClass.getResourceAsStream("/pixiv/recommend-150105774.json")!!.readBytes().decodeToString()

    @Test
    fun anonymousRecommendResponseIsUsable() {
        val response = PikuJson.decodeFromString<PixivRecommendResponse>(payload())

        assertTrue(!response.error)
        assertEquals(18, response.body.illusts.size)
        assertTrue(response.body.illusts.all { it.url.isNotBlank() })
    }

    @Test
    fun recommendItemsMapToWorks() {
        val works = PikuJson.decodeFromString<PixivRecommendResponse>(payload())
            .body.illusts.mapNotNull { it.toWork() }

        assertEquals(18, works.size)
        assertEquals(WorkSource.PIXIV, works.first().source)
        // 卡片靠缩略图撑起来，没图的不成卡
        assertTrue(works.all { it.thumbnailUrl.isNotBlank() })
        assertTrue(works.all { it.id > 0 })
    }
}
