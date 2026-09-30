package com.piku.client.data.remote.ech

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 原生传输返回的一次响应 */
internal class NativeEchResponse(
    val status: Int,
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
)

/**
 * `libpiku_ech.so` 的入口：TLS1.3 + ECH + HTTP/2 全在原生侧做。
 *
 * 为什么不复用 OkHttp 的连接：OkHttp 5 对自定义 SSLSocket 不做任何 socket adapter 匹配
 * （源码注释 "No TLS extensions if the socket class is custom"），拿不到 ALPN 就只能
 * 按 HTTP/1.1 说话，而 ECH 前端门只认 h2。详见 piku-ech/src/lib.rs。
 */
internal object NativeEch {

    init {
        System.loadLibrary("piku_ech")
    }

    /**
     * 失败时原生侧直接抛 IOException（连不上、握手失败、超时……）。
     * [headers] 是 name/value 交替的扁平数组。
     */
    private external fun fetch(
        url: String,
        method: String,
        headers: Array<String>,
        body: ByteArray?,
        echConfig: ByteArray?,
        ip: String,
        timeoutMs: Int,
    ): ByteArray?

    fun fetch(
        url: String,
        method: String,
        headers: List<Pair<String, String>>,
        body: ByteArray?,
        echConfig: ByteArray?,
        ip: String,
        timeoutMs: Int,
    ): NativeEchResponse {
        val flat = ArrayList<String>(headers.size * 2)
        headers.forEach { (name, value) ->
            flat += name
            flat += value
        }
        val frame = fetch(url, method, flat.toTypedArray(), body, echConfig, ip, timeoutMs)
            ?: throw IOException("原生传输没有返回数据")
        return parseEchFrame(frame)
    }
}

/** 帧格式见 piku-ech/src/lib.rs 的 encode()：version(1) status(4) headerCount(4) 头部… bodyLen(4) body */
internal fun parseEchFrame(frame: ByteArray): NativeEchResponse {
    val buffer = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN)
    val version = buffer.get().toInt()
    if (version != ECH_FRAME_VERSION) {
        throw IOException("原生传输帧版本不支持: $version")
    }
    val status = buffer.int
    val headerCount = buffer.int
    val headers = ArrayList<Pair<String, String>>(headerCount)
    repeat(headerCount) {
        headers += readFrameString(buffer) to readFrameString(buffer)
    }
    val body = ByteArray(buffer.int)
    buffer.get(body)
    return NativeEchResponse(status, headers, body)
}

private fun readFrameString(buffer: ByteBuffer): String {
    val bytes = ByteArray(buffer.short.toInt() and 0xFFFF)
    buffer.get(bytes)
    return String(bytes, Charsets.UTF_8)
}

internal const val ECH_FRAME_VERSION = 1
