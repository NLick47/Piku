package com.piku.client.core.translation

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class ImageTranslateEngineTest {

    private val engine = ImageTranslateEngine(OkHttpClient())

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private val payload = ByteArray(256) { (it % 251).toByte() }

    @Test
    fun chatUrlAcceptsBareAndFullForms() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            ImageTranslateEngine.chatUrl("https://api.example.com/v1"),
        )
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            ImageTranslateEngine.chatUrl("https://api.example.com/v1/"),
        )
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            ImageTranslateEngine.chatUrl("https://api.example.com/v1/chat/completions"),
        )
    }

    @Test
    fun extractsDataUrlFromContentArray() {
        val json = """
            {"choices":[{"message":{"content":[
              {"type":"text","text":"done"},
              {"type":"image_url","image_url":{"url":"data:image/png;base64,${b64(payload)}"}}
            ]}}]}
        """.trimIndent()

        assertNotNull(engine.extractImageBytes(json))
        assertEquals(payload.toList(), engine.extractImageBytes(json)!!.toList())
    }

    @Test
    fun extractsB64FromContentArray() {
        val json = """
            {"choices":[{"message":{"content":[{"type":"image","b64_json":"${b64(payload)}"}]}}]}
        """.trimIndent()

        assertEquals(payload.toList(), engine.extractImageBytes(json)!!.toList())
    }

    @Test
    fun extractsFromMessageImagesArray() {
        val json = """
            {"choices":[{"message":{"content":"ok","images":[{"b64_json":"${b64(payload)}"}]}}]}
        """.trimIndent()

        assertEquals(payload.toList(), engine.extractImageBytes(json)!!.toList())
    }

    @Test
    fun extractsFromDataArray() {
        val json = """{"data":[{"b64_json":"${b64(payload)}"}]}"""

        assertEquals(payload.toList(), engine.extractImageBytes(json)!!.toList())
    }

    @Test
    fun extractsFromPlainStringContent() {
        val json = """{"choices":[{"message":{"content":"data:image/png;base64,${b64(payload)}"}}]}"""

        assertEquals(payload.toList(), engine.extractImageBytes(json)!!.toList())
    }

    @Test
    fun extractsFromBareBase64StringContent() {
        val json = """{"choices":[{"message":{"content":"${b64(payload)}"}}]}"""

        assertEquals(payload.toList(), engine.extractImageBytes(json)!!.toList())
    }

    @Test
    fun returnsNullForTextOnlyReply() {
        val json = """{"choices":[{"message":{"content":"I cannot edit images"}}]}"""

        assertNull(engine.extractImageBytes(json))
    }

    @Test
    fun returnsNullForMalformedJson() {
        assertNull(engine.extractImageBytes("not json"))
        assertNull(engine.extractImageBytes(""))
    }

    @Test
    fun ignoresTooSmallPayload() {
        val tiny = b64(ByteArray(8))
        val json = """{"data":[{"b64_json":"$tiny"}]}"""

        assertNull(engine.extractImageBytes(json))
    }

    @Test
    fun editsUrlAcceptsAllForms() {
        assertEquals(
            "https://api.example.com/v1/images/edits",
            ImageTranslateEngine.editsUrl("https://api.example.com/v1"),
        )
        assertEquals(
            "https://api.example.com/v1/images/edits",
            ImageTranslateEngine.editsUrl("https://api.example.com/v1/"),
        )
        assertEquals(
            "https://api.example.com/v1/images/edits",
            ImageTranslateEngine.editsUrl("https://api.example.com/v1/images"),
        )
        assertEquals(
            "https://api.example.com/v1/images/edits",
            ImageTranslateEngine.editsUrl("https://api.example.com/v1/images/edits"),
        )
    }

    @Test
    fun extractsRemoteUrlFromDataArray() {
        val json = """{"created":1,"data":[{"url":"https://cdn.example.com/a.png"}]}"""

        assertEquals("https://cdn.example.com/a.png", engine.extractImageUrl(json))
    }

    @Test
    fun inlineBase64IsNotTreatedAsRemoteUrl() {
        val json = """{"data":[{"b64_json":"${b64(payload)}"}]}"""

        assertNull(engine.extractImageUrl(json))
    }

    @Test
    fun extractsRemoteUrlFromChatContent() {
        val json = """{"choices":[{"message":{"content":[
            {"type":"image_url","image_url":{"url":"https://cdn.example.com/b.png"}}
        ]}}]}"""

        assertEquals("https://cdn.example.com/b.png", engine.extractImageUrl(json))
    }
}
