package com.piku.client.data.remote

import android.util.Log
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * 使用 OkHttp 手动实现的 WebDAV 客户端。
 * 仅支持 Basic Auth，操作：MKCOL / PUT / GET / PROPFIND。
 */
@Singleton
class WebDavClient @Inject constructor(
    @Named("webdav") private val client: OkHttpClient,
) {

    /**
     * 确保远程目录存在：先 PROPFIND 检查，不存在则 MKCOL 创建。
     * 递归创建：对 path 中每一段都尝试 MKCOL（已存在时忽略 405/451）。
     */
    suspend fun ensureDirectory(baseUrl: String, path: String, credentials: String) {
        val dirUrl = buildUrl(baseUrl, path)
        if (checkExists(dirUrl, credentials)) return
        // 递归创建父目录
        val segments = path.trim('/').split("/")
        if (segments.size > 1) {
            val parentPath = segments.dropLast(1).joinToString("/")
            ensureDirectory(baseUrl, parentPath, credentials)
        }
        mkcol(dirUrl, credentials)
    }

    /**
     * 上传文件到 WebDAV。如果父目录不存在会自动创建。
     */
    suspend fun uploadFile(
        baseUrl: String,
        path: String,
        credentials: String,
        data: ByteArray,
        contentType: String = "application/octet-stream",
    ): Boolean {
        val url = buildUrl(baseUrl, path)
        // 确保父目录存在
        val segments = path.trim('/').split("/")
        if (segments.size > 1) {
            val parentPath = segments.dropLast(1).joinToString("/")
            ensureDirectory(baseUrl, parentPath, credentials)
        }
        return put(url, credentials, data, contentType)
    }

    /**
     * 从 WebDAV 下载文件，返回内容字节数组；文件不存在返回 null。
     */
    suspend fun downloadFile(
        baseUrl: String,
        path: String,
        credentials: String,
    ): ByteArray? {
        val url = buildUrl(baseUrl, path)
        return get(url, credentials)
    }

    /**
     * 检查远程文件/目录是否存在（PROPFIND Depth: 0）。
     */
    suspend fun exists(
        baseUrl: String,
        path: String,
        credentials: String,
    ): Boolean {
        val url = buildUrl(baseUrl, path)
        return checkExists(url, credentials)
    }

    /**
     * 测试连接：对 baseUrl 发起 PROPFIND Depth: 0。
     */
    suspend fun testConnection(
        baseUrl: String,
        credentials: String,
    ): Boolean = try {
        val url = ensureTrailingSlash(baseUrl)
        checkExists(url, credentials)
    } catch (e: Exception) {
        Log.e(TAG, "testConnection failed", e)
        false
    }

    private fun buildUrl(baseUrl: String, path: String): String {
        val base = ensureTrailingSlash(baseUrl)
        return "$base${path.trimStart('/')}"
    }

    private fun ensureTrailingSlash(url: String): String =
        if (url.endsWith("/")) url else "$url/"

    private fun checkExists(url: String, credentials: String): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credentials)
            .method("PROPFIND", "".toRequestBody(null))
            .header("Depth", "0")
            .build()
        val response = client.newCall(request).execute()
        response.close()
        // 207 Multi-Status = 存在; 404 = 不存在; 405 也视为已存在（MKCOL 目录已存在时部分服务器返回 405）
        return response.code in listOf(207, 200, 405)
    }

    private fun mkcol(url: String, credentials: String): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credentials)
            .method("MKCOL", "".toRequestBody(null))
            .build()
        val response = client.newCall(request).execute()
        response.close()
        // 201 Created = 成功; 405 Method Not Allowed = 目录已存在
        return response.code in listOf(201, 405)
    }

    private fun put(
        url: String,
        credentials: String,
        data: ByteArray,
        contentType: String,
    ): Boolean {
        val body = data.toRequestBody(contentType.toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credentials)
            .put(body)
            .build()
        val response = client.newCall(request).execute()
        response.close()
        return response.code in listOf(200, 201, 204)
    }

    private fun get(url: String, credentials: String): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credentials)
            .get()
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            return null
        }
        val bytes = response.body?.bytes()
        response.close()
        return bytes
    }

    companion object {
        private const val TAG = "WebDavClient"

        fun basicAuth(username: String, password: String): String =
            Credentials.basic(username, password)
    }
}
