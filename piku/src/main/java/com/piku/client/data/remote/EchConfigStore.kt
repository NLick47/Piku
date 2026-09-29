package com.piku.client.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cloudflare 的共享 ECH 配置。
 *
 * pixiv 自己不发布 ECH 配置，但 CF 的 ECH 是一份**共享配置**：拿 cloudflare-ech.com 的
 * 配置去连它前面的任何站点都成立（外层 SNI 是 cloudflare-ech.com，真实域名加密在内层）。
 * 于是"线路按 SNI 字符串拦"这条规则就看不见我们真正要访问的域名了。
 *
 * 取配置的通道钉 AliDNS 的 IP：不需要解析，握手也不带 SNI，可拦的东西都没有。
 */
class EchConfigStore(
    private val clock: () -> Long = System::currentTimeMillis,
    private val fetcher: () -> String = ::fetchEchRecord,
) {

    private class Entry(val config: ByteArray, val expiresAt: Long)

    @Volatile
    private var cached: Entry? = null
    private val refreshing = AtomicBoolean(false)

    @Volatile
    private var inFlight: CountDownLatch? = null

    /** 当前可用配置；没有或已过期返回 null（调用方按普通 TLS 走），顺手触发一次后台刷新 */
    fun current(): ByteArray? {
        val entry = cached
        if (entry != null && clock() < entry.expiresAt) return entry.config
        refreshAsync()
        return null
    }

    /**
     * 冷启动第一次请求用：没有缓存就等一次刷新，最多等 [timeoutMs]。
     * 取不到就返回 null，由调用方报"ECH 配置不可用"，不无限期挂着。
     */
    fun currentOrFetch(timeoutMs: Long): ByteArray? {
        current()?.let { return it }
        refreshAsync()
        inFlight?.await(timeoutMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
        return current()
    }

    /** 手上有没有还没过期的配置（别走 current()，那会触发刷新） */
    fun isFresh(): Boolean {
        val entry = cached ?: return false
        return clock() < entry.expiresAt
    }

    /** 后台去取一次；已有刷新在跑就直接跳过，不阻塞调用方 */
    fun refreshAsync() {
        if (!refreshing.compareAndSet(false, true)) return
        val latch = CountDownLatch(1)
        inFlight = latch
        executor.execute {
            try {
                parseEchRecord(fetcher())?.let { (config, ttlMs) ->
                    cached = Entry(config, clock() + ttlMs.coerceIn(MIN_TTL_MS, MAX_TTL_MS))
                }
            } catch (_: Exception) {
                // 取不到就继续用旧的；过期后 current() 自然回落普通 TLS
            } finally {
                inFlight = null
                refreshing.set(false)
                latch.countDown()
            }
        }
    }

    companion object {
        const val ALIDNS_ENDPOINT =
            "https://223.5.5.5/resolve?name=cloudflare-ech.com&type=HTTPS"
        const val DEFAULT_TTL_MS = 30 * 60_000L
        private const val MIN_TTL_MS = 5 * 60_000L
        private const val MAX_TTL_MS = 60 * 60_000L

        private val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "piku-ech").apply { isDaemon = true }
        }
    }
}

/** 钉 IP 直连 AliDNS 的 dns-json：主机是 IP 字面量，既不解析、也没有 SNI */
private fun fetchEchRecord(): String {
    val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    val request = Request.Builder()
        .url(EchConfigStore.ALIDNS_ENDPOINT)
        .header("Accept", "application/dns-json")
        .build()
    return client.newCall(request).execute().use { response ->
        response.body?.string().orEmpty()
    }
}

/**
 * 从 dns-json 响应里抠出 ECH 配置与 TTL。
 *
 * AliDNS 给 `ech="..."`（带引号）、Cloudflare 给 `ech=...`（不带），两种都认；
 * base64 可能是 url-safe 变体，一并归一化。
 */
internal fun parseEchRecord(body: String): Pair<ByteArray, Long>? {
    // 响应里的引号是 JSON 转义的（ech=\"...\"），先去转义再取
    val text = body.replace("\\\"", "\"")
    val raw = ECH_PATTERN.find(text)?.groupValues?.get(1) ?: return null
    val padded = raw.replace('-', '+').replace('_', '/')
        .let { it + "=".repeat((4 - it.length % 4) % 4) }
    val bytes = runCatching { Base64.getDecoder().decode(padded) }.getOrNull() ?: return null
    // ECHConfigList 前两字节是列表总长：对不上说明数据不对，宁可不启用
    if (bytes.size < 4) return null
    val declared = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
    if (declared != bytes.size - 2) return null
    val ttlMs = TTL_PATTERN.find(text)?.groupValues?.get(1)?.toLongOrNull()
        ?.times(1000) ?: EchConfigStore.DEFAULT_TTL_MS
    return bytes to ttlMs
}

private val ECH_PATTERN = Regex("""ech="?([A-Za-z0-9+/=_-]+)"?""")
private val TTL_PATTERN = Regex(""""TTL"\s*:\s*(\d+)""")
