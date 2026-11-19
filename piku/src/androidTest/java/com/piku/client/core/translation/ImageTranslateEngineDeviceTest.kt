package com.piku.client.core.translation

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.Deflater

@RunWith(AndroidJUnit4::class)
class ImageTranslateEngineDeviceTest {

    private val baseUrl = "http://10.0.2.2:18080/v1"

    private fun engine() = ImageTranslateEngine(
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build(),
    )

    private fun reachable(): Boolean = runCatching {
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        client.newCall(
            okhttp3.Request.Builder().url("http://10.0.2.2:18080/last").build(),
        ).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    private fun setMode(mode: String) {
        val client = OkHttpClient()
        client.newCall(
            okhttp3.Request.Builder().url("http://10.0.2.2:18080/mode/$mode").build(),
        ).execute().use { }
    }

    private fun png(w: Int, h: Int): ByteArray {
        val raw = ByteArray((w * 3 + 1) * h)
        var p = 0
        repeat(h) {
            raw[p++] = 0
            repeat(w) {
                raw[p++] = 200.toByte()
                raw[p++] = 40
                raw[p++] = 40
            }
        }
        val deflater = Deflater()
        deflater.setInput(raw)
        deflater.finish()
        val out = ByteArray(raw.size + 64)
        val len = deflater.deflate(out)
        deflater.end()

        fun chunk(type: String, data: ByteArray): ByteArray {
            val body = type.toByteArray() + data
            val crc = CRC32().apply { update(body) }.value
            return byteArrayOf(
                (data.size ushr 24).toByte(), (data.size ushr 16).toByte(),
                (data.size ushr 8).toByte(), data.size.toByte(),
            ) + body + byteArrayOf(
                (crc ushr 24).toByte(), (crc ushr 16).toByte(),
                (crc ushr 8).toByte(), crc.toByte(),
            )
        }

        val ihdr = byteArrayOf(
            (w ushr 24).toByte(), (w ushr 16).toByte(), (w ushr 8).toByte(), w.toByte(),
            (h ushr 24).toByte(), (h ushr 16).toByte(), (h ushr 8).toByte(), h.toByte(),
            8, 2, 0, 0, 0,
        )
        return byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10) +
            chunk("IHDR", ihdr) + chunk("IDAT", out.copyOf(len)) + chunk("IEND", ByteArray(0))
    }

    private fun translate(mode: String, apiMode: ImageApiMode = ImageApiMode.CHAT): ImageTranslateResult = runBlocking {
        if (!reachable()) return@runBlocking ImageTranslateResult.Failure(ImageTranslateError.NoModel())
        setMode(mode)
        engine().translate(
            imageBytes = png(24, 24),
            prompt = "translate this page",
            targetLang = "zh",
            baseUrl = baseUrl,
            model = "mock-image",
            apiKey = "sk-test",
            apiMode = apiMode,
        )
    }

    @Test
    fun decodesContentArrayReply() {
        val result = translate("content_array")
        if (!reachable()) return
        assertTrue("期望成功，实际 $result", result is ImageTranslateResult.Success)
        val bitmap = (result as ImageTranslateResult.Success).bitmap
        assertNotNull(bitmap)
        assertTrue("应解出有效尺寸", bitmap.width > 0 && bitmap.height > 0)
    }

    @Test
    fun decodesDataArrayReply() {
        val result = translate("data_array")
        if (!reachable()) return
        assertTrue(result is ImageTranslateResult.Success)
    }

    @Test
    fun decodesMessageImagesReply() {
        val result = translate("message_images")
        if (!reachable()) return
        assertTrue(result is ImageTranslateResult.Success)
    }

    @Test
    fun decodesPlainDataUrlReply() {
        val result = translate("plain_dataurl")
        if (!reachable()) return
        assertTrue(result is ImageTranslateResult.Success)
    }

    @Test
    fun decodesPlainBase64Reply() {
        val result = translate("plain_b64")
        if (!reachable()) return
        assertTrue(result is ImageTranslateResult.Success)
    }

    @Test
    fun textOnlyReplyIsBadResponse() {
        val result = translate("text_only")
        if (!reachable()) return
        assertTrue(result is ImageTranslateResult.Failure)
        assertTrue(
            "文本回复应归 BadResponse，实际 ${(result as ImageTranslateResult.Failure).error}",
            result.error is ImageTranslateError.BadResponse,
        )
    }

    @Test
    fun serverErrorIsUpstream() {
        val result = translate("e500")
        if (!reachable()) return
        val error = (result as ImageTranslateResult.Failure).error
        assertTrue("5xx 应归 Upstream，实际 $error", error is ImageTranslateError.Upstream)
    }

    @Test
    fun rateLimitIsRetryable() {
        val result = translate("e429")
        if (!reachable()) return
        val error = (result as ImageTranslateResult.Failure).error
        assertTrue(error is ImageTranslateError.RateLimited)
        assertEquals(7L, (error as ImageTranslateError.RateLimited).retryAfterSec)
        assertTrue(error.retryable)
    }

    @Test
    fun editsModeDecodesB64() {
        val result = translate("b64", apiMode = ImageApiMode.EDITS)
        if (!reachable()) return
        assertTrue("期望成功，实际 $result", result is ImageTranslateResult.Success)
    }

    @Test
    fun editsModeDownloadsRemoteUrl() {
        val result = translate("url", apiMode = ImageApiMode.EDITS)
        if (!reachable()) return
        assertTrue("外链应被下载并解码，实际 $result", result is ImageTranslateResult.Success)
        val bitmap = (result as ImageTranslateResult.Success).bitmap
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
    }

    @Test
    fun editsModeServerErrorIsUpstream() {
        val result = translate("e500", apiMode = ImageApiMode.EDITS)
        if (!reachable()) return
        val error = (result as ImageTranslateResult.Failure).error
        assertTrue("5xx 应归 Upstream，实际 $error", error is ImageTranslateError.Upstream)
    }

    @Test
    fun editsModeHitsEditsEndpointWithImagesArray() {
        val result = translate("b64", apiMode = ImageApiMode.EDITS)
        if (!reachable()) return
        assertTrue(result is ImageTranslateResult.Success)

        val client = OkHttpClient()
        val last = client.newCall(
            okhttp3.Request.Builder().url("http://10.0.2.2:18080/last").build(),
        ).execute().use { it.body?.string().orEmpty() }
        assertTrue("应记录到 images 字段，实际 $last", last.contains("\"has_image\": true"))
    }
}
