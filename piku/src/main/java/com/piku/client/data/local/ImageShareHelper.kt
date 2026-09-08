package com.piku.client.data.local

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageShareHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {


    suspend fun getImageUri(url: String, workId: Long, page: Int): Uri =
        withContext(Dispatchers.IO) {
            val cacheDir = File(context.cacheDir, "share_tmp").apply { mkdirs() }
            val extension = url.substringAfterLast('.', "")
                .substringBefore('?')
                .lowercase()
                .takeIf { it.length in 2..4 && it.all(Char::isLetterOrDigit) }
            val fileName = "Piku_${workId}_${page + 1}.${extension ?: "jpg"}"
            val file = File(cacheDir, fileName)

            // 如果缓存文件已存在且未损坏，直接复用
            if (!file.exists() || file.length() == 0L) {
                val request = Request.Builder().url(url).build()
                val tmp = File.createTempFile("${fileName}_", ".tmp", cacheDir)
                try {
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        val body = response.body ?: throw IOException("empty body")
                        body.byteStream().use { input ->
                            tmp.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    if (tmp.length() == 0L) throw IOException("empty body")
                    // 原子替换：同目录 rename 不跨文件系统，失败才退回直接覆盖
                    if (!tmp.renameTo(file)) {
                        tmp.copyTo(file, overwrite = true)
                    }
                } finally {
                    tmp.delete()
                }
            }

            evictExpired(cacheDir, keep = file)

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }


    suspend fun cleanupCache() = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "share_tmp")
        if (!cacheDir.exists()) return@withContext
        evictExpired(cacheDir, keep = null)
    }

    private fun evictExpired(cacheDir: File, keep: File?) {
        val maxAge = 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        cacheDir.listFiles()?.forEach { file ->
            if (file != keep && now - file.lastModified() > maxAge) {
                file.delete()
            }
        }
    }
}
