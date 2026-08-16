package com.piku.client.data.local

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把远程图片下载并保存到系统相册（Pictures/Piku）。
 *
 * 使用注入的 OkHttpClient（带 cookie / Referer / UA 拦截器），
 * poipiku 的图片地址对 Referer 有要求，不能新建裸 client。
 * API 29+ 走 MediaStore RELATIVE_PATH 免权限；API 26-28 需调用方先申请
 * WRITE_EXTERNAL_STORAGE。
 */
@Singleton
class ImageSaver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {

    /** 下载 [url] 并保存；成功返回相册 Uri，失败抛异常（由调用方决定如何提示）。 */
    suspend fun save(url: String, baseName: String): Uri = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val bytes = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.bytes() ?: throw IOException("empty body")
        }
        val extension = url.substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.length in 2..4 && it.all(Char::isLetterOrDigit) }
        val mime = if (extension == "png") "image/png" else "image/jpeg"
        val displayName = "$baseName.${extension ?: "jpg"}"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Piku")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IOException("open output stream failed")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            // 写失败时清理半成品记录，避免相册里出现损坏的空文件
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }
}
