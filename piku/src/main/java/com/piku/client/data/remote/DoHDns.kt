package com.piku.client.data.remote

import android.util.Log
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 防 DNS 污染（双源解析）：
 * - 业务域名（*.poipiku.com）返回 [系统 DNS IP, DoH IP] 合并列表，系统 IP 在前。
 *   系统 DNS 准确时（多数网络）直连系统 IP，零 DoH 往返；系统 DNS 被污染时
 *   （假 IP 连不上）OkHttp 自动换下一个 IP，落到 DoH 的真实 IP 上。
 *   SNI 恒清空/伪装（见 [PoipikuNetworkPolicy]），DoH IP 握手必然成功。
 * - 系统 DNS 结果短缓存（30s）：污染地址不值得缓存太久；DoH 结果 10 分钟
 *   缓存 + 后台定时刷新保持新鲜。
 * - 两路任一路失败时只用另一路；都失败时抛异常（OkHttp 按 UnknownHostException 处理）。
 */
class DoHDns : Dns {

    private val doh: DnsOverHttps = DnsOverHttps.Builder()
        .client(
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build(),
        )
        .url(DOH_URL.toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName(DOH_IPV4),
            InetAddress.getByName(DOH_IPV6),
        )
        .build()

    private val systemCache = ConcurrentHashMap<String, CacheEntry>()

    private val dohCache = ConcurrentHashMap<String, CacheEntry>()

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "piku-doh-refresh").apply { isDaemon = true }
    }

    @Volatile
    private var started = false

    /** 启动 DoH 后台刷新任务（幂等）。首次刷新发生在第一个周期后，不预热。 */
    fun start() {
        if (started) return
        started = true
        scheduler.scheduleWithFixedDelay(
            { refreshBusinessDomains() },
            REFRESH_INTERVAL_MS,
            REFRESH_INTERVAL_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    override fun lookup(hostname: String): List<InetAddress> {
        if (!isBusinessDomain(hostname)) {
            return Dns.SYSTEM.lookup(hostname)
        }
        val now = System.currentTimeMillis()
        var systemAddresses = systemCache[hostname]?.takeIf { now < it.expiresAt }?.addresses
        if (systemAddresses == null) {
            systemAddresses = try {
                Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                emptyList()
            }
            if (systemAddresses.isNotEmpty()) {
                systemCache[hostname] = CacheEntry(systemAddresses, now + SYSTEM_CACHE_TTL_MS)
            }
        }
        var dohAddresses = dohCache[hostname]?.takeIf { now < it.expiresAt }?.addresses
        if (dohAddresses == null) {
            dohAddresses = try {
                doh.lookup(hostname)
            } catch (e: Exception) {
                emptyList()
            }
            if (dohAddresses.isNotEmpty()) {
                dohCache[hostname] = CacheEntry(dohAddresses, now + DOH_CACHE_TTL_MS)
            }
        }
        if (systemAddresses.isEmpty() && dohAddresses.isEmpty()) {
            throw UnknownHostException("both system and DoH resolution failed for $hostname")
        }
        val merged = LinkedHashSet<InetAddress>()
        merged.addAll(systemAddresses)
        merged.addAll(dohAddresses)
        Log.d(
            "PikuDiag",
            "dns: $hostname sys=[${systemAddresses.joinToString { it.hostAddress }}] " +
                "doh=[${dohAddresses.joinToString { it.hostAddress }}]",
        )
        return merged.toList()
    }

    private fun refreshBusinessDomains() {
        val now = System.currentTimeMillis()
        for (hostname in BUSINESS_DOMAINS) {
            try {
                val addresses = doh.lookup(hostname)
                if (addresses.isNotEmpty()) {
                    dohCache[hostname] = CacheEntry(addresses, now + DOH_CACHE_TTL_MS)
                }
            } catch (e: Exception) {
                // 刷新失败保留旧值，等下一轮再试
            }
        }
    }

    private fun isBusinessDomain(hostname: String): Boolean =
        hostname == "poipiku.com" || hostname.endsWith(".poipiku.com")

    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val expiresAt: Long,
    )

    private companion object {
        const val DOH_URL = "https://dns.alidns.com/dns-query"
        const val DOH_IPV4 = "223.5.5.5"
        const val DOH_IPV6 = "2400:3200::1"
        const val SYSTEM_CACHE_TTL_MS = 30 * 1000L
        const val DOH_CACHE_TTL_MS = 10 * 60 * 1000L
        const val REFRESH_INTERVAL_MS = 10 * 60 * 1000L
        val BUSINESS_DOMAINS = listOf("poipiku.com", "cdn.poipiku.com")
    }
}