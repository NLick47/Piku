package com.piku.client.data.remote

import android.content.SharedPreferences
import android.util.Log
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/**
 * 仅向 OkHttp 返回完成真实 TLS 握手和证书校验的业务域名地址。
 *
 * 最近成功地址会写入 SharedPreferences。进程重启后的首次请求先验证这些地址；
 * 300ms 内没有成功地址时，系统 DNS 与 DoH 才会并行解析并参与 TLS 竞速。
 */
class DoHDns(
    private val prefs: SharedPreferences,
) : Dns {

    private class DohSource(
        val url: String,
        val bootstrap: List<InetAddress>,
    ) {
        val client: DnsOverHttps = DnsOverHttps.Builder()
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(DOH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .readTimeout(DOH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .build(),
            )
            .url(url.toHttpUrl())
            .bootstrapDnsHosts(*bootstrap.toTypedArray())
            .build()
    }

    private val dohSources: List<DohSource> = listOf(
        DohSource(
            url = "https://dns.alidns.com/dns-query",
            bootstrap = listOf(
                InetAddress.getByName("223.5.5.5"),
                InetAddress.getByName("2400:3200::1"),
            ),
        ),
        DohSource(
            url = "https://cloudflare-dns.com/dns-query",
            bootstrap = listOf(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("2606:4700:4700::1111"),
            ),
        ),
    )

    private val socketFactory = SniStrippingSocketFactory()
    private val hostnameVerifier = PoipikuHostnameVerifier()
    private val systemCache = ConcurrentHashMap<String, AddressCacheEntry>()
    private val dohCache = ConcurrentHashMap<String, AddressCacheEntry>()
    private val winners = ConcurrentHashMap<String, WinnerEntry>()
    private val failures = ConcurrentHashMap<String, FailureEntry>()
    private val inflight = ConcurrentHashMap<String, CompletableFuture<List<InetAddress>>>()

    private val sourceExecutor = Executors.newFixedThreadPool(5) { runnable ->
        Thread(runnable, "piku-dns-source").apply { isDaemon = true }
    }
    private val probeExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "piku-ip-probe").apply { isDaemon = true }
    }
    override fun lookup(hostname: String): List<InetAddress> {
        if (!isBusinessDomain(hostname)) return Dns.SYSTEM.lookup(hostname)

        val now = System.currentTimeMillis()
        winners[hostname]
            ?.takeIf { now < it.expiresAt && !isFailed(hostname, it.address, now) }
            ?.let { return listOf(it.address) }

        // 同一域名并发 miss 时共享同一次竞速，避免首屏等场景重复解析与探测。
        inflight[hostname]?.let { return awaitRace(it, hostname) }
        val future = CompletableFuture<List<InetAddress>>()
        val existing = inflight.putIfAbsent(hostname, future)
        if (existing != null) return awaitRace(existing, hostname)
        try {
            val result = resolveRace(hostname, now)
            future.complete(result)
            return result
        } catch (e: Exception) {
            future.completeExceptionally(e)
            throw e
        } finally {
            inflight.remove(hostname, future)
        }
    }

    private fun awaitRace(
        future: CompletableFuture<List<InetAddress>>,
        hostname: String,
    ): List<InetAddress> = try {
        future.get()
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw UnknownHostException("lookup interrupted for $hostname")
    } catch (e: ExecutionException) {
        throw (e.cause as? UnknownHostException)
            ?: UnknownHostException("lookup failed for $hostname")
    }

    private fun resolveRace(hostname: String, now: Long): List<InetAddress> {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RESOLUTION_TIMEOUT_MS)
        val completion = ExecutorCompletionService<InetAddress?>(sourceExecutor)
        val tasks = mutableListOf<Future<InetAddress?>>()
        var completedTasks = 0
        val persisted = persistedAddresses(hostname, now)
        if (persisted.isNotEmpty()) {
            tasks += completion.submit { probeFirst(hostname, persisted) }
            completion.poll(PERSISTED_HEAD_START_MS, TimeUnit.MILLISECONDS)?.let { completed ->
                completedTasks++
                completed.get()?.let { winner ->
                    acceptWinner(hostname, winner)
                    return listOf(winner)
                }
            }
        }

        tasks += completion.submit {
            probeFirst(hostname, resolveSystem(hostname))
        }
        dohSources.forEachIndexed { index, _ ->
            tasks += completion.submit {
                probeFirst(hostname, resolveDoh(hostname, index))
            }
        }

        var remaining = tasks.size - completedTasks
        try {
            while (remaining > 0) {
                val waitNanos = deadline - System.nanoTime()
                if (waitNanos <= 0) break
                val completed = completion.poll(waitNanos, TimeUnit.NANOSECONDS) ?: break
                remaining--
                completed.get()?.let { winner ->
                    tasks.forEach { if (!it.isDone) it.cancel(true) }
                    acceptWinner(hostname, winner)
                    return listOf(winner)
                }
            }
        } finally {
            tasks.forEach { if (!it.isDone) it.cancel(true) }
        }
        throw UnknownHostException("no TLS-verified address for $hostname")
    }

    /** 主客户端成功完成 TLS 握手后刷新赢家和持久化记录。 */
    fun reportSuccess(hostname: String, address: InetAddress) {
        if (!isBusinessDomain(hostname)) return
        acceptWinner(hostname, address)
    }

    /** 主客户端连接失败时立即淘汰对应赢家，避免后续请求继续命中。 */
    fun reportFailure(hostname: String, address: InetAddress, type: FailureType) {
        if (!isBusinessDomain(hostname)) return
        winners.computeIfPresent(hostname) { _, winner ->
            if (winner.address == address) null else winner
        }
        val ttl = ttlFor(type)
        failures[failureKey(hostname, address)] = FailureEntry(
            expiresAt = System.currentTimeMillis() + ttl,
        )
        if (type == FailureType.TLS) removePersistedAddress(hostname, address)
    }

    private fun ttlFor(type: FailureType): Long = when (type) {
        FailureType.TLS -> TLS_FAILURE_TTL_MS
        FailureType.CONNECT -> CONNECT_FAILURE_TTL_MS
        FailureType.STREAM -> STREAM_FAILURE_TTL_MS
    }

    /**
     * 强制下一次 lookup 跳过赢家缓存，从保留的解析缓存 + 黑名单过滤中换 IP，
     * 不重新查询 DNS。由重试拦截器在连接失败后调用。
     *
     * 解析缓存（systemCache/dohCache）保持有效；仅当缓存内地址全部被封禁时，
     * [resolveSystem]/[resolveDoh] 才会忽略缓存重新查询。
     */
    fun forceReResolve(hostname: String) {
        winners.remove(hostname)
    }

    private fun resolveSystem(hostname: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        val cached = systemCache[hostname]
        if (cached != null && now < cached.expiresAt &&
            cached.addresses.any { !isFailed(hostname, it, now) }
        ) {
            return cached.addresses
        }
        val addresses = try {
            Dns.SYSTEM.lookup(hostname)
        } catch (_: Exception) {
            emptyList()
        }
        if (addresses.isNotEmpty()) {
            systemCache[hostname] = AddressCacheEntry(addresses, now + SYSTEM_CACHE_TTL_MS)
        }
        return addresses
    }

    private fun resolveDoh(hostname: String, sourceIndex: Int): List<InetAddress> {
        val now = System.currentTimeMillis()
        val cacheKey = "$sourceIndex|$hostname"
        val cached = dohCache[cacheKey]
        if (cached != null && now < cached.expiresAt &&
            cached.addresses.any { !isFailed(hostname, it, now) }
        ) {
            return cached.addresses
        }
        val addresses = try {
            dohSources[sourceIndex].client.lookup(hostname)
        } catch (_: Exception) {
            emptyList()
        }
        if (addresses.isNotEmpty()) {
            dohCache[cacheKey] = AddressCacheEntry(addresses, now + DOH_CACHE_TTL_MS)
        }
        return addresses
    }

    private fun probeFirst(hostname: String, addresses: List<InetAddress>): InetAddress? {
        val now = System.currentTimeMillis()
        val candidates = addresses.distinct().filterNot { isFailed(hostname, it, now) }
        if (candidates.isEmpty()) return null

        val completion = ExecutorCompletionService<InetAddress?>(probeExecutor)
        val probes = candidates.map { address ->
            completion.submit { probe(hostname, address) }
        }
        try {
            repeat(probes.size) {
                completion.take().get()?.let { winner ->
                    probes.forEach { if (!it.isDone) it.cancel(true) }
                    return winner
                }
            }
        } finally {
            probes.forEach { if (!it.isDone) it.cancel(true) }
        }
        return null
    }

    private fun probe(hostname: String, address: InetAddress): InetAddress? {
        val startedAt = System.currentTimeMillis()
        val rawSocket = Socket()
        try {
            try {
                rawSocket.connect(InetSocketAddress(address, HTTPS_PORT), TCP_PROBE_TIMEOUT_MS)
            } catch (e: Exception) {
                recordProbeFailure(hostname, address, FailureType.CONNECT, startedAt)
                return null
            }

            try {
                val sslSocket = socketFactory.createSocket(rawSocket, hostname, HTTPS_PORT, true) as SSLSocket
                sslSocket.use {
                    it.soTimeout = TLS_HANDSHAKE_TIMEOUT_MS
                    it.startHandshake()
                    if (!hostnameVerifier.verify(hostname, it.session)) {
                        recordProbeFailure(hostname, address, FailureType.TLS, startedAt)
                        return null
                    }
                }
            } catch (e: SSLException) {
                recordProbeFailure(hostname, address, FailureType.TLS, startedAt)
                return null
            } catch (e: Exception) {
                recordProbeFailure(hostname, address, FailureType.CONNECT, startedAt)
                return null
            }
            return address
        } finally {
            runCatching { rawSocket.close() }
        }
    }

    private fun acceptWinner(hostname: String, address: InetAddress) {
        val now = System.currentTimeMillis()
        failures.remove(failureKey(hostname, address))
        winners[hostname] = WinnerEntry(address, now, now + WINNER_TTL_MS)
        persistSuccess(hostname, address, now)
        Log.d("PikuDiag", "dns verified: $hostname -> ${address.hostAddress}")
    }

    private fun recordProbeFailure(
        hostname: String,
        address: InetAddress,
        type: FailureType,
        probeStartedAt: Long,
    ) {
        val currentWinner = winners[hostname]
        if (currentWinner?.address == address && currentWinner.verifiedAt >= probeStartedAt) return
        val ttl = ttlFor(type)
        failures[failureKey(hostname, address)] = FailureEntry(System.currentTimeMillis() + ttl)
        winners.computeIfPresent(hostname) { _, winner ->
            if (winner.address == address) null else winner
        }
        if (type == FailureType.TLS) removePersistedAddress(hostname, address)
    }

    private fun isFailed(hostname: String, address: InetAddress, now: Long): Boolean {
        val key = failureKey(hostname, address)
        val failure = failures[key] ?: return false
        if (now < failure.expiresAt) return true
        failures.remove(key, failure)
        return false
    }

    private fun persistedAddresses(hostname: String, now: Long): List<InetAddress> =
        readPersisted(hostname)
            .filter { now - it.succeededAt < PERSISTED_TTL_MS }
            .mapNotNull { runCatching { InetAddress.getByName(it.address) }.getOrNull() }

    private fun persistSuccess(hostname: String, address: InetAddress, now: Long) {
        val hostAddress = address.hostAddress ?: return
        val current = readPersisted(hostname)
        val existing = current.firstOrNull { it.address == hostAddress }
        if (existing != null && now - existing.succeededAt < PERSIST_WRITE_INTERVAL_MS) return
        val updated = listOf(PersistedAddress(hostAddress, now)) +
            current.filterNot { it.address == hostAddress }.take(MAX_PERSISTED_IPS - 1)
        prefs.edit().putString(persistKey(hostname), encodePersisted(updated)).apply()
    }

    private fun removePersistedAddress(hostname: String, address: InetAddress) {
        val updated = readPersisted(hostname).filterNot { it.address == address.hostAddress }
        prefs.edit().putString(persistKey(hostname), encodePersisted(updated)).apply()
    }

    private fun readPersisted(hostname: String): List<PersistedAddress> =
        prefs.getString(persistKey(hostname), null)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val separator = line.lastIndexOf('|')
                if (separator <= 0) return@mapNotNull null
                val timestamp = line.substring(separator + 1).toLongOrNull() ?: return@mapNotNull null
                PersistedAddress(line.substring(0, separator), timestamp)
            }
            ?.take(MAX_PERSISTED_IPS)
            ?.toList()
            .orEmpty()

    private fun encodePersisted(addresses: List<PersistedAddress>): String =
        addresses.joinToString("\n") { "${it.address}|${it.succeededAt}" }

    private fun isBusinessDomain(hostname: String): Boolean =
        hostname == "poipiku.com" || hostname.endsWith(".poipiku.com")

    private fun failureKey(hostname: String, address: InetAddress) = "$hostname|${address.hostAddress}"
    private fun persistKey(hostname: String) = "$PERSIST_PREFIX$hostname"

    private data class AddressCacheEntry(val addresses: List<InetAddress>, val expiresAt: Long)
    private data class WinnerEntry(val address: InetAddress, val verifiedAt: Long, val expiresAt: Long)
    private data class FailureEntry(val expiresAt: Long)
    private data class PersistedAddress(val address: String, val succeededAt: Long)
    enum class FailureType { TLS, CONNECT, STREAM }

    private companion object {
        const val HTTPS_PORT = 443
        const val TCP_PROBE_TIMEOUT_MS = 3_000
        const val TLS_HANDSHAKE_TIMEOUT_MS = 2_000
        const val DOH_TIMEOUT_MS = 5_000L
        const val PERSISTED_HEAD_START_MS = 300L
        const val RESOLUTION_TIMEOUT_MS = 11_000L
        const val SYSTEM_CACHE_TTL_MS = 30_000L
        const val WINNER_TTL_MS = 60_000L
        const val CONNECT_FAILURE_TTL_MS = 2 * 60_000L
        const val STREAM_FAILURE_TTL_MS = 15_000L
        const val TLS_FAILURE_TTL_MS = 10 * 60_000L
        const val DOH_CACHE_TTL_MS = 10 * 60_000L
        const val PERSISTED_TTL_MS = 7 * 24 * 60 * 60_000L
        const val PERSIST_WRITE_INTERVAL_MS = 60 * 60_000L
        const val MAX_PERSISTED_IPS = 4
        const val PERSIST_PREFIX = "trusted_dns_ip_"
    }
}
