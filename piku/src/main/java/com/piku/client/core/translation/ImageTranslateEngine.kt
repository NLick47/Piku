package com.piku.client.core.translation

import com.piku.client.R

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.util.Base64
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** 图片翻译失败原因；[retryable] 决定 ViewModel 是否自动重试 */
sealed class ImageTranslateError(message: String? = null) : Exception(message) {
    abstract val retryable: Boolean

    /** 模型拒绝（安全策略）或未产图——重试不会变 */
    class Refused(val detail: String?) : ImageTranslateError(detail) {
        override val retryable get() = false
    }

    /** 限流——按上游 Retry-After 退避后可重试 */
    class RateLimited(val retryAfterSec: Long?) : ImageTranslateError("rate limited") {
        override val retryable get() = true
    }

    /** 上游/代理 5xx 或其他非 429/422 错误 */
    class Upstream(val code: Int) : ImageTranslateError("upstream error $code") {
        override val retryable get() = true
    }

    /** 网络错误 / 引擎层超时 */
    class Network(cause: Throwable? = null) : ImageTranslateError(cause?.message) {
        override val retryable get() = true
    }

    /** 响应 200 但解不出图片 */
    class BadResponse : ImageTranslateError("undecodable image") {
        override val retryable get() = true
    }

    /** 原图下载失败 */
    class DownloadFailed : ImageTranslateError("download failed") {
        override val retryable get() = true
    }

    /** 目录里没有可用的 image 模型 */
    class NoModel : ImageTranslateError("no image model") {
        override val retryable get() = false
    }
}

sealed interface ImageTranslateResult {
    data class Success(val bitmap: Bitmap) : ImageTranslateResult
    data class Failure(val error: ImageTranslateError) : ImageTranslateResult
}

@Singleton
class ImageTranslateEngine @Inject constructor(
    @Named("translate") val client: OkHttpClient,
) {

    suspend fun translate(
        imageBytes: ByteArray,
        prompt: String,
        targetLang: String,
        baseUrl: String,
        model: String,
        apiKey: String,
        authMode: AuthMode = AuthMode.BEARER,
        authHeader: String = ManualProvider.DEFAULT_AUTH_HEADER,
        extraHeaders: Map<String, String> = emptyMap(),
        params: Map<String, JsonElement> = emptyMap(),
        apiMode: ImageApiMode = ImageApiMode.CHAT,
    ): ImageTranslateResult = withContext(Dispatchers.IO) {
        if (authMode != AuthMode.NONE && apiKey.isBlank()) {
            return@withContext ImageTranslateResult.Failure(ImageTranslateError.Upstream(401))
        }
        val sendBytes = if (imageBytes.size > MAX_INPUT_BYTES) {
            compressImage(imageBytes) ?: imageBytes
        } else {
            imageBytes
        }

        val headers = llmAuthHeaders(authMode, authHeader, apiKey, extraHeaders)
            .toMutableMap()
            .apply { putIfAbsent("Content-Type", "application/json") }
        val (url, body) = when (apiMode) {
            ImageApiMode.CHAT -> chatUrl(baseUrl) to buildChatBody(sendBytes, prompt, model, params)
            ImageApiMode.EDITS -> editsUrl(baseUrl) to buildEditsBody(sendBytes, prompt, model, params)
        }
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val raw = runCatching { response.body?.string() }.getOrNull()
                if (!response.isSuccessful) {
                    val retryAfter = response.header("Retry-After")?.toLongOrNull()
                    Log.w(TAG, "translate failed: ${response.code} ${raw?.take(300)}")
                    return@withContext ImageTranslateResult.Failure(
                        classifyHttpError(response.code, retryAfter, raw),
                    )
                }
                val remote = raw?.let { extractImageUrl(it) }
                val decoded = when {
                    remote != null -> fetchRemote(remote)
                    raw != null -> extractImageBytes(raw)
                    else -> null
                } ?: return@withContext ImageTranslateResult.Failure(
                    ImageTranslateError.BadResponse(),
                )
                val bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                    ?: return@withContext ImageTranslateResult.Failure(ImageTranslateError.BadResponse())
                ImageTranslateResult.Success(bitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "translate error: ${e::class.simpleName}: ${e.message}")
            ImageTranslateResult.Failure(ImageTranslateError.Network(e))
        }
    }

    private fun buildEditsBody(
        imageBytes: ByteArray,
        prompt: String,
        model: String,
        params: Map<String, JsonElement>,
    ): JsonObject {
        val dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes)
        return buildJsonObject {
            put("model", JsonPrimitive(model))
            put("prompt", JsonPrimitive(prompt))
            put(
                "images",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("image_url", JsonPrimitive(dataUrl))
                        },
                    )
                },
            )
            params.forEach { (k, v) -> if (k !in EDITS_RESERVED_KEYS) put(k, v) }
        }
    }

    private fun buildChatBody(
        imageBytes: ByteArray,
        prompt: String,
        model: String,
        params: Map<String, JsonElement>,
    ): JsonObject {
        val dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes)
        return buildJsonObject {
            put("model", JsonPrimitive(model))
            put("stream", JsonPrimitive(false))
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", JsonPrimitive("user"))
                            put(
                                "content",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("type", JsonPrimitive("text"))
                                            put("text", JsonPrimitive(prompt))
                                        },
                                    )
                                    add(
                                        buildJsonObject {
                                            put("type", JsonPrimitive("image_url"))
                                            put(
                                                "image_url",
                                                buildJsonObject { put("url", JsonPrimitive(dataUrl)) },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
            params.forEach { (k, v) -> if (k !in RESERVED_KEYS) put(k, v) }
        }
    }

            internal fun extractImageUrl(raw: String): String? {
        val root = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val candidates = mutableListOf<JsonElement>()
        (root["data"] as? JsonArray)?.forEach { el ->
            (el as? JsonObject)?.get("url")?.let { candidates += it }
        }
        (root["choices"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.let { msg ->
            (msg["content"] as? JsonArray)?.forEach { el ->
                (el as? JsonObject)?.get("image_url")?.let { candidates += it }
            }
            (msg["images"] as? JsonArray)?.forEach { el ->
                (el as? JsonObject)?.get("url")?.let { candidates += it }
            }
        }
        return candidates.firstNotNullOfOrNull { el ->
            val text = when (el) {
                is JsonPrimitive -> el.contentOrNull
                is JsonObject -> (el["url"] as? JsonPrimitive)?.contentOrNull
                else -> null
            }
            text?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        }
    }

    private fun fetchRemote(url: String): ByteArray? = runCatching {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.bytes()
        }
    }.getOrNull()?.takeIf { it.size >= MIN_IMAGE_BYTES }

    internal fun extractImageBytes(raw: String): ByteArray? {
        val root = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val message = (root["choices"] as? JsonArray)
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
        if (message != null) {
            when (val content = message["content"]) {
                is JsonArray -> content.forEach { element ->
                    val obj = element as? JsonObject ?: return@forEach
                    obj["image_url"]?.let { return decodeImageUrl(it) }
                    obj["b64_json"]?.let { return decodeBase64(it) }
                    obj["image"]?.let { return decodeImageUrl(it) }
                }
                is JsonPrimitive -> content.contentOrNull?.let { text ->
                    decodeDataUrl(text)?.let { return it }
                    decodeBase64OrNull(text)?.let { return it }
                }
                else -> Unit
            }
            (message["images"] as? JsonArray)?.forEach { element ->
                val obj = element as? JsonObject ?: return@forEach
                obj["b64_json"]?.let { return decodeBase64(it) }
                obj["image_url"]?.let { return decodeImageUrl(it) }
                obj["url"]?.let { return decodeImageUrl(it) }
            }
        }
        (root["data"] as? JsonArray)?.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            obj["b64_json"]?.let { return decodeBase64(it) }
            obj["url"]?.let { return decodeImageUrl(it) }
        }
        return null
    }

    private fun decodeImageUrl(element: JsonElement): ByteArray? {
        val url = when (element) {
            is JsonPrimitive -> element.contentOrNull
            is JsonObject -> (element["url"] as? JsonPrimitive)?.contentOrNull
            else -> null
        } ?: return null
        return decodeDataUrl(url)
    }

    private fun decodeDataUrl(text: String): ByteArray? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("data:")) return null
        val comma = trimmed.indexOf(',')
        if (comma < 0) return null
        val meta = trimmed.substring(0, comma)
        val payload = trimmed.substring(comma + 1)
        return if (meta.contains("base64", ignoreCase = true)) {
            decodeBase64OrNull(payload)
        } else {
            runCatching { URLDecoder.decode(payload, "UTF-8").toByteArray() }.getOrNull()
        }
    }

    private fun decodeBase64(element: JsonElement): ByteArray? =
        (element as? JsonPrimitive)?.contentOrNull?.let(::decodeBase64OrNull)

    private fun decodeBase64OrNull(text: String): ByteArray? {
        val cleaned = text.filterNot { it.isWhitespace() }
        if (cleaned.isEmpty()) return null
        return runCatching { Base64.getDecoder().decode(cleaned) }
            .getOrNull()
            ?.takeIf { it.size >= MIN_IMAGE_BYTES }
    }

    /** 代理错误契约：429=限流，422=拒绝/未产图（body 带 type+detail），其余归上游错误 */
    private fun classifyHttpError(code: Int, retryAfter: Long?, body: String?): ImageTranslateError {
        if (code == 429) return ImageTranslateError.RateLimited(retryAfter)
        if (code == 422) {
            val detail = runCatching { JSONObject(body.orEmpty()).optString("detail") }
                .getOrNull()?.takeIf { it.isNotBlank() }
            return ImageTranslateError.Refused(detail)
        }
        return ImageTranslateError.Upstream(code)
    }

    /**
     * 压缩过大的图片到 2048px 以内
     */
    private fun compressImage(bytes: ByteArray): ByteArray? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val maxDim = maxOf(opts.outWidth, opts.outHeight)
        if (maxDim <= 2048) return null

        // Calculate the largest power of 2 that is <= targetSize
        var sampleSize = 1
        while (maxDim / sampleSize > 2048) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            ?: return null

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    companion object {
        private const val TAG = "ImageTranslate"
        private const val MAX_INPUT_BYTES = 2 * 1024 * 1024
        private const val MIN_IMAGE_BYTES = 128

        private val JSON_MEDIA = "application/json".toMediaType()

        private val RESERVED_KEYS = setOf("model", "messages", "stream", "n")

        private val EDITS_RESERVED_KEYS = setOf("model", "prompt", "images", "n", "stream")

        internal fun chatUrl(baseUrl: String): String {
            val trimmed = baseUrl.trimEnd('/')
            return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
        }

        internal fun editsUrl(baseUrl: String): String {
            val trimmed = baseUrl.trimEnd('/')
            return when {
                trimmed.endsWith("/images/edits") -> trimmed
                trimmed.endsWith("/images") -> "$trimmed/edits"
                else -> "$trimmed/images/edits"
            }
        }
    }
}

data class ImageTranslateConfig(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val authMode: AuthMode = AuthMode.BEARER,
    val authHeader: String = ManualProvider.DEFAULT_AUTH_HEADER,
    val extraHeaders: Map<String, String> = emptyMap(),
    val params: Map<String, JsonElement> = emptyMap(),
    val apiMode: ImageApiMode = ImageApiMode.CHAT,
)

fun ImageTranslateError.toMessageRes(): Int = when (this) {
    is ImageTranslateError.NoModel -> R.string.image_translate_no_model
    is ImageTranslateError.DownloadFailed -> R.string.image_translate_download_failed
    is ImageTranslateError.RateLimited -> R.string.image_translate_rate_limited
    else -> R.string.detail_image_translate_failed
}
