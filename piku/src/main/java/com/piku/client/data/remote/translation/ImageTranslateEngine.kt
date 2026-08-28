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
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

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
     * @return 翻译后的 Bitmap，失败返回 null
     */
    suspend fun translate(
        imageBytes: ByteArray,
        prompt: String,
        targetLang: String,
        proxyBaseUrl: String,
    ): Bitmap? = withContext(Dispatchers.IO) {
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
                    Log.w(TAG, "translate failed: ${response.code}")
                    return@withContext null
                }
                val bytes = response.body?.bytes() ?: return@withContext null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "translate error: ${e.message}")
            null
        }
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
