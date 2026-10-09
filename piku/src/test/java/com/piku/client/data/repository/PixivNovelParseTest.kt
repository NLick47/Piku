package com.piku.client.data.repository

import com.piku.client.data.remote.PikuJson
import com.piku.client.data.remote.pixiv.PixivNovelEmbeddedImage
import com.piku.client.data.remote.pixiv.PixivWebviewNovel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** webview 小说载荷的解析：真实形状是空集合给 []、非空才是 {id: {...}} */
class PixivNovelParseTest {

    @Test
    fun decodesPayloadWithEmptyCollections() {
        // 2026-10 线上原样：illusts/images 空时是数组，按 Map 硬解会整篇读不出来
        val json = """
            {"text":"本文です[pixivimage:80000000-1]","title":"t","illusts":[],"images":[]}
        """.trimIndent()
        val novel = PikuJson.decodeFromString<PixivWebviewNovel>(json)
        assertEquals("本文です[pixivimage:80000000-1]", novel.text)
        assertTrue(novel.images.isEmpty())
    }

    @Test
    fun decodesPayloadWithUploadedImages() {
        val json = """
            {"text":"[uploadedimage:25879370]","illusts":[],
             "images":{"25879370":{"novelImageId":"25879370","urls":{"480mw":"https://i.pximg.net/a.jpg"}}}}
        """.trimIndent()
        val novel = PikuJson.decodeFromString<PixivWebviewNovel>(json)
        assertEquals("https://i.pximg.net/a.jpg", novel.images.getValue("25879370").urls["480mw"])
    }

    @Test
    fun uploadedImageMarkerBecomesToken() {
        val embedded = mapOf(
            "25879370" to PixivNovelEmbeddedImage(
                novelImageId = "25879370",
                urls = mapOf("480mw" to "https://i.pximg.net/a.jpg", "original" to "https://i.pximg.net/o.jpg"),
            ),
        )
        assertEquals("前\n[nimg:https://i.pximg.net/a.jpg]\n后", applyUploadedImages("前[uploadedimage:25879370]后", embedded))
    }

    @Test
    fun unknownUploadedImageMarkerIsLeftForCleanup() {
        val text = applyUploadedImages("前[uploadedimage:1]后", emptyMap())
        assertEquals("前[uploadedimage:1]后", text)
        assertEquals("前后", cleanPixivNovelText(text))
    }
}
