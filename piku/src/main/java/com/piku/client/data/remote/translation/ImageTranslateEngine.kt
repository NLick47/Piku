package com.piku.client.data.remote.translation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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

/**
 * 图片翻译引擎：调用代理的 /v1/translate-image 端点。
 *
 * 与文本翻译引擎 [LlmTranslateEngine] 完全独立：
 * - 走 multipart/form-data 而非 JSON chat completions
 * - 返回 binary image 而非文本
 * - 代理内部处理 base64 ↔ Gemini API 转换
 */
@Singleton
class ImageTranslateEngine @Inject constructor(
    @Named("translate") val client: OkHttpClient,
) {

    /**
     * 翻译单页图片
     *
     * @param imageBytes 原图 binary（PNG/JPEG）
     * @param prompt 翻译提示词（来自 catalog image 模式）
     * @param targetLang 目标语言代码（zh/en/ja）
     * @param proxyBaseUrl 代理地址（如 http://47.86.19.39:43981）
     */
    suspend fun translate(
        imageBytes: ByteArray,
        prompt: String,
        targetLang: String,
        proxyBaseUrl: String,
    ): ImageTranslateResult = withContext(Dispatchers.IO) {
        // Compress if too large (> 2MB)
        val sendBytes = if (imageBytes.size > 2 * 1024 * 1024) {
            compressImage(imageBytes) ?: imageBytes
        } else {
            imageBytes
        }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image", "page.png",
                sendBytes.toRequestBody("image/png".toMediaType()),
            )
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("target_lang", targetLang)
            .build()

        val request = Request.Builder()
            .url(proxyBaseUrl.trimEnd('/'))
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val retryAfter = response.header("Retry-After")?.toLongOrNull()
                    val errorBody = runCatching { response.body?.string() }.getOrNull()
                    Log.w(TAG, "translate failed: ${response.code} ${errorBody?.take(200)}")
                    return@withContext ImageTranslateResult.Failure(
                        classifyHttpError(response.code, retryAfter, errorBody),
                    )
                }
                val bytes = response.body?.bytes()
                    ?: return@withContext ImageTranslateResult.Failure(ImageTranslateError.BadResponse())
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@withContext ImageTranslateResult.Failure(ImageTranslateError.BadResponse())
                ImageTranslateResult.Success(bitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "translate error: ${e::class.simpleName}: ${e.message}")
            ImageTranslateResult.Failure(ImageTranslateError.Network(e))
        }
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
    }
}
