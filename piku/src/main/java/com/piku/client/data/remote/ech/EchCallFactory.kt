package com.piku.client.data.remote.ech

import android.util.Log
import com.piku.client.data.remote.NetworkRuntime
import com.piku.client.data.remote.RetryPolicy
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.reflect.KClass
import okio.Buffer
import okio.Timeout

/**
 * pixiv 的传输层：交给原生 ECH 通道（TLS1.3 + ECH + HTTP/2）。
 *
 * Retrofit 只认 `Call.Factory`，所以换掉传输不影响接口定义、序列化与调用方。
 * 拦截器那套（UA/Referer/Accept）在 [buildHeaders] 里补齐。
 *
 * 取消是"尽力而为"：原生调用是阻塞的，cancel 只标记状态并在返回后丢弃结果。
 */
class EchCallFactory(
    private val echConfig: () -> ByteArray?,
    private val endpoints: (String) -> List<String>,
    private val userAgent: String,
    private val authHeaders: (String) -> List<Pair<String, String>> = { emptyList() },
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val runtime: NetworkRuntime = NetworkRuntime(),
) : Call.Factory {

    /** 与 poipiku 通道共用一套重试语义；这里额度收紧到重试一次 */
    private val policy = RetryPolicy(maxAttempts = RETRY_ATTEMPTS)

    override fun newCall(request: Request): Call = EchCall(request)

    private inner class EchCall(private val original: Request) : Call {
        private val executed = AtomicBoolean(false)
        private val canceled = AtomicBoolean(false)

        override fun request(): Request = original

        override fun execute(): Response {
            check(executed.compareAndSet(false, true)) { "Already Executed" }
            if (canceled.get()) throw IOException("Canceled")
            return perform()
        }

        override fun enqueue(responseCallback: Callback) {
            check(executed.compareAndSet(false, true)) { "Already Executed" }
            Thread({
                try {
                    val response = perform()
                    if (canceled.get()) {
                        response.close()
                        responseCallback.onFailure(this, IOException("Canceled"))
                    } else {
                        responseCallback.onResponse(this, response)
                    }
                } catch (e: IOException) {
                    responseCallback.onFailure(this, e)
                } catch (e: Throwable) {
                    responseCallback.onFailure(this, IOException(e))
                }
            }, THREAD_NAME).apply { isDaemon = true }.start()
        }

        override fun cancel() {
            canceled.set(true)
        }

        override fun isCanceled(): Boolean = canceled.get()

        override fun isExecuted(): Boolean = executed.get()

        override fun timeout(): Timeout = Timeout.NONE

        override fun clone(): Call = EchCall(original)

        // 原生通道不发 OkHttp 事件，也没有拦截器体系，这几个只是把接口补齐
        override fun addEventListener(eventListener: EventListener) = Unit

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> tag(type: KClass<T>): T? = tags[type.java] as T? ?: original.tag(type)

        @Suppress("UNCHECKED_CAST")
        override fun <T> tag(type: Class<out T>): T? = tags[type] as T? ?: original.tag(type)

        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T =
            tag(type.java, computeIfAbsent)

        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T =
            tag(type) ?: computeIfAbsent().also { tags[type] = it }

        private val tags = ConcurrentHashMap<Class<*>, Any>()

        private fun perform(): Response {
            val host = original.url.host
            val body = original.body?.let { requestBody ->
                Buffer().also { requestBody.writeTo(it) }.readByteArray()
            }
            val headers = buildHeaders(body?.size)
            val where = "${original.method} ${original.url.host}${original.url.encodedPath}"
            var attempts = RetryPolicy.Attempts()
            while (true) {
                if (canceled.get()) throw IOException("Canceled")
                val config = echConfig() ?: throw IOException("ECH 配置不可用（pixiv 直连依赖它）")
                // 这条通道不走 OkHttp 拦截器，出错时只能靠这行看清"到底发了什么上线路"
                Log.d(
                    TAG,
                    "ech $where bodyLen=${body?.size ?: 0} headers=${headers.joinToString(",") { it.first }}",
                )

                val response = try {
                    send(config, host, body, headers)
                } catch (e: IOException) {
                    if (canceled.get()) throw IOException("Canceled")
                    val decision = policy.ioFailure(e, original.method == "GET", attempts)
                    if (decision !is RetryPolicy.Decision.Retry) {
                        Log.w(TAG, "ech failed $where: ${e.message}")
                        throw e
                    }
                    attempts = decision.attempts
                    Log.w(TAG, "ech retry $where attempt=${attempts.io} ${e.javaClass.simpleName}: ${e.message}")
                    runtime.sleeper(decision.delayMs)
                    continue
                }
                if (response.isSuccessful) return response
                val decision = policy.httpFailure(response.code, original.method == "GET", attempts)
                if (decision !is RetryPolicy.Decision.Retry) {
                    // 首页把所有失败都显示成同一句「网络错误」，服务端到底回了什么只能靠这行
                    Log.w(TAG, "ech ${response.code} $where")
                    return response
                }
                attempts = decision.attempts
                response.close()
                Log.w(TAG, "ech retry http=${response.code} $where attempt=${attempts.http}")
                runtime.sleeper(decision.delayMs)
            }
        }

        /** 试一轮：所有已知地址挨个试，全失败抛最后一个错 */
        private fun send(
            config: ByteArray,
            host: String,
            body: ByteArray?,
            headers: List<Pair<String, String>>,
        ): Response {
            var lastError: IOException? = null
            for (ip in endpoints(host)) {
                try {
                    val native = NativeEch.fetch(
                        url = original.url.toString(),
                        method = original.method,
                        headers = headers,
                        body = body,
                        echConfig = config,
                        ip = ip,
                        timeoutMs = timeoutMs,
                    )
                    return native.toOkHttpResponse(original)
                } catch (e: IOException) {
                    lastError = if (lastError == null) e else lastError
                    if (canceled.get()) throw IOException("Canceled")
                }
            }
            throw lastError ?: IOException("没有可用的 pixiv 地址")
        }

        private fun buildHeaders(bodyLength: Int?): List<Pair<String, String>> {
            val headers = ArrayList<Pair<String, String>>(original.headers.size + 6)
            original.headers.forEach { (name, value) -> headers += name to value }
            fun fill(name: String, value: String) {
                if (headers.none { it.first.equals(name, ignoreCase = true) }) headers += name to value
            }
            fill("User-Agent", userAgent)
            fill("Referer", "https://www.pixiv.net/")
            fill("Accept", "application/json")
            // 原生侧不解压，明确要原文
            fill("Accept-Encoding", "identity")
            // 本通道是自定义 Call.Factory，OkHttp 的 BridgeInterceptor 不参与，
            // 而这两个头平时正是它从请求体里补的：少了 Content-Type，服务端不会把请求体当
            // 表单解析——pixiv 的 token 端点会回 invalid_client「body 里找不到 client_id」。
            // GET 无请求体，所以这条通道此前一直没暴露过。
            original.body?.contentType()?.let { fill("Content-Type", it.toString()) }
            if (bodyLength != null) fill("Content-Length", bodyLength.toString())
            // 调用方显式设过的头优先，鉴权头只补空缺
            for ((name, value) in authHeaders(original.url.host)) {
                if (headers.none { it.first.equals(name, ignoreCase = true) }) headers += name to value
            }
            return headers
        }
    }

    private companion object {
        const val TAG = "PikuDiag"
        const val THREAD_NAME = "piku-ech-call"
        const val DEFAULT_TIMEOUT_MS = 30_000

        /** 初次 + 重试一次：冷启动的网络抖动够用，又不会把失败拖得太久 */
        const val RETRY_ATTEMPTS = 2
    }
}

private fun NativeEchResponse.toOkHttpResponse(request: Request): Response {
    val builder = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_2)
        .code(status)
        .message(statusMessage(status))

    var mediaType: MediaType? = null
    var gzipped = false
    for ((name, value) in headers) {
        when {
            name.equals("content-encoding", ignoreCase = true) -> gzipped = value.contains("gzip", ignoreCase = true)
            name.equals("content-type", ignoreCase = true) -> {
                mediaType = value.toMediaTypeOrNull()
                builder.addHeader(name, value)
            }
            else -> builder.addHeader(name, value)
        }
    }

    val payload = if (gzipped) gunzip(body) else body
    return builder.body(payload.toResponseBody(mediaType)).build()
}

private fun gunzip(body: ByteArray): ByteArray =
    GZIPInputStream(body.inputStream()).use { it.readBytes() }

/** 只为了让 Response 的非空 message 看起来正常；Retrofit 不看它 */
private fun statusMessage(status: Int): String = when (status) {
    200 -> "OK"
    204 -> "No Content"
    301 -> "Moved Permanently"
    302 -> "Found"
    303 -> "See Other"
    304 -> "Not Modified"
    307 -> "Temporary Redirect"
    308 -> "Permanent Redirect"
    400 -> "Bad Request"
    401 -> "Unauthorized"
    403 -> "Forbidden"
    404 -> "Not Found"
    429 -> "Too Many Requests"
    500 -> "Internal Server Error"
    502 -> "Bad Gateway"
    503 -> "Service Unavailable"
    else -> "HTTP $status"
}
