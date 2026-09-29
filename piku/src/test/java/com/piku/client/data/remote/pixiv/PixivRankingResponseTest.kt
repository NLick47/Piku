package com.piku.client.data.remote.pixiv

import com.piku.client.data.remote.PikuJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivRankingResponseTest {

    private fun payload(name: String): String =
        javaClass.getResourceAsStream("/pixiv/$name")!!.readBytes().decodeToString()

    private fun decode(name: String): PixivRankingResponse =
        PikuJson.decodeFromString(payload(name))

    @Test
    fun dailyWithUnviewablePlaceholderStillParses() {
        val response = decode("daily-p1.json")

        assertEquals(500, response.rankTotal)
        assertEquals(50, response.contents.size)

        // 第 35 名（下标 34）：limit_unviewable 占位，混型字段的源头
        val placeholder = response.contents[34]
        assertEquals(150133214L, placeholder.illustId)
        assertEquals("", placeholder.title)
        assertEquals(1, placeholder.pageCount)
        assertEquals(0, placeholder.contentType.sexual)

        // 正常条目的字符串页数照样换算
        assertTrue(response.contents[0].pageCount >= 1)
    }

    @Test
    fun weeklyWithCleanShapeStillParses() {
        val response = decode("weekly-p1.json")

        assertEquals(500, response.rankTotal)
        assertEquals(50, response.contents.size)
    }
}
