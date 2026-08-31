package com.piku.client.data.remote

import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * WebDAV 协议错误码分类。
 *
 * - [OK]：2xx 或 405（MKCOL 时部分服务器对已存在目录返回 405）
 * - [NOT_FOUND]：404，调用方应视为"不存在"而非失败
 * - [UNAUTHORIZED]：401/403，调用方应视为凭据错并终止
 * - [OTHER]：5xx 或其他，服务端问题
 */
enum class WebDavResult { OK, NOT_FOUND, UNAUTHORIZED, OTHER }

class WebDavException(val result: WebDavResult, val code: Int, message: String) :
    RuntimeException("WebDAV $code: $message")

/**
 * 使用 OkHttp 实现的 WebDAV 客户端，仅支持 Basic Auth。
 * 操作：MKCOL / PUT / GET / PROPFIND。
 *
 * 所有 IO 都在 [Dispatchers.IO] 上执行，并把协程取消信号桥接到 OkHttp [Call.cancel]，
 * 避免 ViewModel 退出后孤儿 PUT 继续占用带宽。
 */
@Singleton
class WebDavClient @Inject constructor(
    @Named("webdav") private val client: OkHttpClient,
) {

    /**
     * 测试连接：只发一个 PROPFIND Depth: 0 到 baseUrl，不创建任何目录。
     * 凭据错就抛 WebDavException(UNAUTHORIZED)，网络错抛 WebDavException(OTHER)。
     */
    suspend fun ping(baseUrl: String, credentials: String) {
        val url = ensureTrailingSlash(baseUrl)
        withContext(Dispatchers.IO) {
            val result = propfind(url, credentials)
            when (result) {
                WebDavResult.OK, WebDavResult.NOT_FOUND -> Unit
                WebDavResult.UNAUTHORIZED -> throw WebDavException(
                    result, 401, "认证失败，请检查用户名和密码",
                )
                WebDavResult.OTHER -> throw WebDavException(
                    result, 0, "无法连接到服务器",
                )
            }
        }
    }

    /**
     * 确保远程目录存在：先 PROPFIND 检查，不存在则 MKCOL 创建。
     * 递归创建父目录。
     */
    suspend fun ensureDirectory(baseUrl: String, path: String, credentials: String) {
        withContext(Dispatchers.IO) {
            val dirUrl = buildUrl(baseUrl, path)
            if (checkExists(dirUrl, credentials)) return@withContext
            val segments = path.trim('/').split("/")
            if (segments.size > 1) {
                val parentPath = segments.dropLast(1).joinToString("/")
                ensureDirectory(baseUrl, parentPath, credentials)
            }
            mkcol(dirUrl, credentials)
        }
    }

    /**
     * 上传文件到 WebDAV。父目录不存在会自动创建。
     */
    suspend fun uploadFile(
        baseUrl: String,
        path: String,
        credentials: String,
        data: ByteArray,
        contentType: String = "application/octet-stream",
    ): Boolean = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, path)
        val segments = path.trim('/').split("/")
        if (segments.size > 1) {
            val parentPath = segments.dropLast(1).joinToString("/")
            ensureDirectory(baseUrl, parentPath, credentials)
        }
        put(url, credentials, data, contentType)
    }

    /**
     * 从 WebDAV 下载文件。文件不存在返回 null。
     */
    suspend fun downloadFile(
        baseUrl: String,
        path: String,
        credentials: String,
    ): ByteArray? = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, path)
        get(url, credentials)
    }

    /**
     * 检查远程文件/目录是否存在。
     */
    suspend fun exists(
        baseUrl: String,
        path: String,
        credentials: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, path)
        checkExists(url, credentials)
    }

    private fun buildUrl(baseUrl: String, path: String): String {
        val base = ensureTrailingSlash(baseUrl)
        return "$base${path.trimStart('/')}"
    }

    private fun ensureTrailingSlash(url: String): String =
        if (url.endsWith("/")) url else "$url/"

    private suspend fun checkExists(url: String, credentials: String): Boolean {
        return when (propfind(url, credentials)) {
            WebDavResult.OK -> true
            WebDavResult.NOT_FOUND -> false
            WebDavResult.UNAUTHORIZED -> throw WebDavException(
                WebDavResult.UNAUTHORIZED, 401, "认证失败",
            )
            WebDavResult.OTHER -> false
        }
    }

    private suspend fun mkcol(url: String, credentials: String): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credentials)
            .method("MKCOL", "".toRequestBody(null))
            .build()
        val response = executeCancellable(request)
        response.use { r ->
            when (r.code) {
                201 -> return true
                405 -> return true
                401, 403 -> throw WebDavException(
                    WebDavResult.UNAUTHORIZED, r.code, "认证失败",
                )
            }
            return false
        }
    }

    private suspend fun put(
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
        val response = executeCancellable(request)
        response.use { r ->
            if (r.isSuccessful) return true
            if (r.code == 401 || r.code == 403) {
                throw WebDavException(
                    WebDavResult.UNAUTHORIZED, r.code, "认证失败",
                )
            }
            return false
        }
    }

    private suspend fun get(url: String, credentials: String): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credentials)
            .get()
            .build()
        val response = executeCancellable(request)
        response.use { r ->
            if (!r.isSuccessful) {
                if (r.code == 401 || r.code == 403) {
                    throw WebDavException(
                        WebDavResult.UNAUTHORIZED, r.code, "认证失败",
                    )
                }
                return null
            }
            return r.body?.bytes()
        }
    }

    private suspend fun propfind(url: String, credentials: String): WebDavResult {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credentials)
            .method("PROPFIND", "".toRequestBody(null))
            .header("Depth", "0")
            .build()
        val response = executeCancellable(request)
        response.use { r ->
            return when {
                r.code == 207 || r.code == 200 || r.code == 405 -> WebDavResult.OK
                r.code == 404 -> WebDavResult.NOT_FOUND
                r.code == 401 || r.code == 403 -> WebDavResult.UNAUTHORIZED
                else -> WebDavResult.OTHER
            }
        }
    }

    /**
     * 异步执行 OkHttp 请求，并把协程取消信号桥接到 [Call.cancel]。
     * 协程在网络 IO 期间被取消时，OkHttp 会中断正在进行的 socket 读写，
     * 不会再继续把 PUT 写完。
     */
    private suspend fun executeCancellable(request: Request): Response =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation {
                try {
                    call.cancel()
                } catch (e: Exception) {
                    Log.w(TAG, "cancel call failed", e)
                }
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    if (call.isCanceled() && cont is CancellableContinuation<*>) {
                        cont.cancel(e)
                    } else {
                        cont.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response)
                }
            })
        }

    companion object {
        private const val TAG = "WebDavClient"

        fun basicAuth(username: String, password: String): String =
            Credentials.basic(username, password)
    }
}
