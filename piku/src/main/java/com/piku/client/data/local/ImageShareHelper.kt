package com.piku.client.data.local

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import coil3.SingletonImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageShareHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {

    private val lastCleanupAt = AtomicLong(0L)

    suspend fun getImageUri(url: String, workId: Long, page: Int): Uri =
        withContext(Dispatchers.IO) {
            val cacheDir = File(context.cacheDir, "share_tmp").apply { mkdirs() }
            val extension = url.substringAfterLast('.', "")
                .substringBefore('?')
                .lowercase()
                .takeIf { it.length in 2..4 && it.all(Char::isLetterOrDigit) }
            val fileName = "Piku_${workId}_${page + 1}.${extension ?: "jpg"}"
            val file = File(cacheDir, fileName)

            if (!file.exists() || file.length() == 0L) {
                // 已显示过的图在 Coil 磁盘缓存里有原文件，本地复制比回源快得多
                if (!copyFromImageCache(url, file)) download(url, cacheDir, fileName, file)
            }

            evict(cacheDir, keep = file)

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }


    /** Coil 磁盘缓存键就是 URL；未命中返回 false */
    private fun copyFromImageCache(url: String, target: File): Boolean {
        val snapshot = runCatching {
            SingletonImageLoader.get(context).diskCache?.openSnapshot(url)
        }.getOrNull() ?: return false
        return try {
            val source = snapshot.data.toFile()
            source.length() > 0 && source.copyTo(target, overwrite = true).length() > 0
        } catch (e: Exception) {
            false
        } finally {
            snapshot.close()
        }
    }

    private fun download(url: String, cacheDir: File, fileName: String, target: File) {
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
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
            }
        } finally {
            tmp.delete()
        }
    }

    /** 回到前台时清掉过期文件（MainActivity.onResume），内部有节流 */
    suspend fun cleanupCache() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastCleanupAt.get() < CLEANUP_MIN_INTERVAL_MS) return@withContext
        lastCleanupAt.set(now)
        val cacheDir = File(context.cacheDir, "share_tmp")
        if (!cacheDir.exists()) return@withContext
        evict(cacheDir, keep = null)
    }

    // 不能分享完就删：URI 是异步授权给接收方的，对方可能还没读
    private fun evict(cacheDir: File, keep: File?) {
        val now = System.currentTimeMillis()
        val files = cacheDir.listFiles() ?: return
        val (stale, alive) = files.partition { it != keep && now - it.lastModified() > SHARE_TTL_MS }
        stale.forEach { it.delete() }
        val overflow = alive.count { it != keep } - MAX_SHARE_FILES
        if (overflow > 0) {
            alive.filter { it != keep }
                .sortedBy { it.lastModified() }
                .take(overflow)
                .forEach { it.delete() }
        }
    }

    private companion object {
        const val SHARE_TTL_MS = 30 * 60 * 1000L
        const val MAX_SHARE_FILES = 5
        const val CLEANUP_MIN_INTERVAL_MS = 5 * 60 * 1000L
    }
}
