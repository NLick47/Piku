package com.piku.client.data.remote

import android.content.SharedPreferences
import android.util.Log
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * 仅向 OkHttp 返回完成真实 TLS 握手和证书校验的业务域名地址。
 *
 * 最近成功地址会写入 SharedPreferences。进程重启后的首次请求先验证这些地址；
 * 300ms 内没有成功地址时，系统 DNS 与 DoH 才会并行解析并参与 TLS 竞速。
 *
 * 选谁、给几条、失败的地址多久能再试，交给 [AddressHealth] 与 [AddressSelector]；
 * 这里只负责编排（竞速、去重、持久化）。一次 lookup 返回的是**有序候选**，
 * 赢家在最先，其余已知地址作备选，OkHttp 在同一次请求内就能换 IP。
 */
class DoHDns internal constructor(
    private val prefs: SharedPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
    private val prober: AddressProbe = TlsAddressProbe(),
    jitter: (Long) -> Long = AddressHealth.RANDOM_JITTER,
    private val diagnostics: NetworkDiagnostics = NetworkDiagnostics(),
    private val sourcesOverride: List<AddressSource> = emptyList(),
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

    private val health = AddressHealth(clock, jitter = jitter)
    private val selector = AddressSelector(health, clock)

    /** 内置固定 IP：域名解析失效（投毒、域名过期）时仍有一条可用的路 */
    private val staticSource by lazy { StaticAddressSource() }
    private val systemSource by lazy {
        CachingAddressSource(SYSTEM_CACHE_TTL_MS, clock, ::isUsable, name = SYSTEM_SOURCE_NAME) { hostname ->
            Dns.SYSTEM.lookup(hostname)
        }
    }
    private val alidnsSource by lazy {
        dohSource("alidns", "https://dns.alidns.com/dns-query", listOf("223.5.5.5", "2400:3200::1"))
    }
    private val cloudflareSource by lazy {
        dohSource("cloudflare", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "2606:4700:4700::1111"))
    }

    /** 自建 DoH（CF→CF 的 cloudflare-dns.com）：名单末尾，也是竞速全灭后的最后一问 */
    private val workerSource by lazy {
        dohSource(WORKER_SOURCE_NAME, WORKER_DOH_URL, listOf("172.66.44.124", "172.66.47.132"))
    }

    /**
     * 竞速来源按域名分两套：pixiv 不走系统 DNS（投毒答案对它没有价值，只多花一次探测），
     * 其余域名保持系统 DNS 打头。单测用假来源整体覆盖。
     */
    private val pixivSources: List<AddressSource> by lazy {
        listOf(staticSource, cloudflareSource, workerSource)
    }
    private val otherSources: List<AddressSource> by lazy {
        listOf(systemSource, alidnsSource, cloudflareSource, workerSource)
    }

    internal fun sourcesFor(hostname: String): List<AddressSource> = when {
        sourcesOverride.isNotEmpty() -> sourcesOverride
        isPixivDomain(hostname) -> pixivSources
        else -> otherSources
    }

    internal fun sourceNamesFor(hostname: String): List<String> = sourcesFor(hostname).map { sourceName(it) }

    private val winners = ConcurrentHashMap<String, WinnerEntry>()
    private val inflight = ConcurrentHashMap<String, CompletableFuture<List<InetAddress>>>()

    /** 每个域名最近一次解析的链路，供诊断报告；迟到的来源会继续落进列表 */
    private val traces = ConcurrentHashMap<String, ResolveTrace>()

    private val sourceExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "piku-dns-source").apply { isDaemon = true }
    }
    private val probeExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "piku-ip-probe").apply { isDaemon = true }
    }
    override fun lookup(hostname: String): List<InetAddress> {
        if (!isBusinessDomain(hostname)) return Dns.SYSTEM.lookup(hostname)

        val now = clock()
        val winner = winners[hostname]
            ?.takeIf { now < it.expiresAt && !health.isOnProbation(hostname, it.address) }
        if (winner != null) return routes(hostname, winner.address, now)

        // 同一域名并发 miss 时共享同一次竞速，避免首屏等场景重复解析与探测。
        inflight[hostname]?.let { return awaitRace(it, hostname) }
        val future = CompletableFuture<List<InetAddress>>()
        val existing = inflight.putIfAbsent(hostname, future)
        if (existing != null) return awaitRace(existing, hostname)
        try {
            val result = resolveRace(hostname)
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

    /** 竞速任务的 get：任务异常/中断一律视为该源无结果，不向 OkHttp 泄漏非 UnknownHostException。 */
    private fun safeGet(future: Future<InetAddress?>): InetAddress? = try {
        future.get()
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        null
    } catch (e: ExecutionException) {
        null
    }

    private fun resolveRace(hostname: String): List<InetAddress> {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RESOLUTION_TIMEOUT_MS)
        val completion = ExecutorCompletionService<InetAddress?>(sourceExecutor)
        val tasks = mutableListOf<Future<InetAddress?>>()
        val racedAt = clock()
        val sourcesSeen = ConcurrentLinkedQueue<SourceTrace>()
        val probesSeen = ConcurrentLinkedQueue<ProbeTrace>()
        var completedTasks = 0
        val persisted = persistedAddresses(hostname, clock())
        if (persisted.isNotEmpty()) {
            tasks += completion.submit { probeSource(hostname, persisted, PERSISTED_VIA, probesSeen) }
            completion.poll(PERSISTED_HEAD_START_MS, TimeUnit.MILLISECONDS)?.let { completed ->
                completedTasks++
                safeGet(completed)?.let { winner ->
                    return acceptAndRoute(hostname, winner, racedAt, sourcesSeen, probesSeen)
                }
            }
        }

        sourcesFor(hostname).forEach { source ->
            tasks += completion.submit {
                resolveVia(source, hostname, sourcesSeen, probesSeen)
            }
        }

        var remaining = tasks.size - completedTasks
        try {
            while (remaining > 0) {
                val waitNanos = deadline - System.nanoTime()
                if (waitNanos <= 0) break
                val completed = completion.poll(waitNanos, TimeUnit.NANOSECONDS) ?: break
                remaining--
                safeGet(completed)?.let { winner ->
                    // 刻意不取消其余任务：它们手里的地址正在通过 TLS 校验，
                    // 取消等于把"污染 DNS 下的退路"丢掉。可靠性优先，不省这点流量
                    return acceptAndRoute(hostname, winner, racedAt, sourcesSeen, probesSeen)
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        // 兜底一：还有曾经验证过的地址时，交给真实请求判定，而不是抛"域名不存在"——
        // 后者会让 App 看起来像没网，用户唯一的出路是重启
        val fallback = selector.order(
            hostname,
            persistedAddresses(hostname, clock()),
            NetworkTuning.MAX_FALLBACK_ROUTES,
            includeWaiting = true,
        )
        if (fallback.isNotEmpty()) {
            traces[hostname] = ResolveTrace(
                hostname = hostname,
                elapsedMs = clock() - racedAt,
                sources = sourcesSeen,
                probes = probesSeen,
                winner = null,
                routed = fallback.map { it.hostAddress.orEmpty() },
                fallback = true,
            )
            diagnostics.warn("resolve fallback host=$hostname -> ${fallback.first().hostAddress}")
            return fallback
        }

        // 兜底二：一个可试地址都没有时，单独再问一次名单末尾的来源（自建 DoH）：
        // 它是唯一在任何网络下都干净的出口，上一次失败可能只是瞬时
        sourcesFor(hostname).lastOrNull()?.let { lastResort ->
            resolveVia(lastResort, hostname, sourcesSeen, probesSeen)?.let { winner ->
                return acceptAndRoute(hostname, winner, racedAt, sourcesSeen, probesSeen)
            }
        }

        traces[hostname] = ResolveTrace(
            hostname = hostname,
            elapsedMs = clock() - racedAt,
            sources = sourcesSeen,
            probes = probesSeen,
            winner = null,
            routed = emptyList(),
            fallback = true,
        )
        diagnostics.warn("resolve failed host=$hostname: no verified address")
        throw UnknownHostException("no TLS-verified address for $hostname")
    }

    /** 单个来源的一次解析：记链路 + 探测候选，返回第一个通过 TLS 校验的地址 */
    private fun resolveVia(
        source: AddressSource,
        hostname: String,
        sourcesSeen: MutableCollection<SourceTrace>,
        probesSeen: MutableCollection<ProbeTrace>,
    ): InetAddress? {
        val via = sourceName(source)
        val startedAt = clock()
        val resolved = runCatching { source.resolve(hostname) }
        val answers = resolved.getOrDefault(emptyList())
        // 被中断（调用方取消）不算这个来源失败，照实记会误报"DoH 不可用"
        val cancelled = resolved.exceptionOrNull()?.isCancellation() == true
        sourcesSeen += SourceTrace(
            name = via,
            answers = answers.map { it.hostAddress.orEmpty() },
            candidates = answers.distinct().count { !health.isOnProbation(hostname, it) },
            elapsedMs = clock() - startedAt,
            // 记下失败原因：诊断要能区分"连不上/被拦"与"查询成功但没有记录"
            detail = resolved.exceptionOrNull()?.takeUnless { cancelled }?.describeChain(),
            cancelled = cancelled,
        )
        return probeSource(hostname, answers, via, probesSeen)
    }

    private fun acceptAndRoute(
        hostname: String,
        winner: InetAddress,
        racedAt: Long,
        sourcesSeen: Collection<SourceTrace>,
        probesSeen: Collection<ProbeTrace>,
    ): List<InetAddress> {
        diagnostics.info("resolve ok host=$hostname addr=${winner.hostAddress}")
        acceptWinner(hostname, winner)
        val routes = routes(hostname, winner, clock())
        val winnerAddress = winner.hostAddress.orEmpty()
        traces[hostname] = ResolveTrace(
            hostname = hostname,
            elapsedMs = clock() - racedAt,
            sources = sourcesSeen,
            probes = probesSeen,
            winner = winnerAddress,
            routed = routes.map { it.hostAddress.orEmpty() },
            fallback = false,
        )
        return routes
    }

    private fun sourceName(source: AddressSource): String =
        (source as? NamedAddressSource)?.name ?: "未知来源"

    /** 诊断用：最近一次解析的链路快照（来源 → 探测 → 采用） */
    internal fun lastTrace(hostname: String): ResolveTrace? = traces[hostname]

    /** 诊断用：当前赢家与候选池里每个地址的健康度 */
    internal fun status(hostname: String): HostStatus {
        val now = clock()
        val winner = winners[hostname]?.takeIf { now < it.expiresAt }
        val persisted = persistedAddresses(hostname, now)
        val pool = buildList {
            winner?.let { add(it.address) }
            addAll(persisted)
            addAll(cachedAddresses(hostname))
            addAll(health.snapshot(hostname).map { it.address })
        }.distinct()
        return HostStatus(
            hostname = hostname,
            winner = winner?.address?.hostAddress,
            winnerAgeMs = winner?.let { now - it.verifiedAt },
            addresses = pool.map { address ->
                val failures = health.failures(hostname, address)
                val retryIn = health.retryAt(hostname, address) - now
                AddressStatus(
                    address = address.hostAddress.orEmpty(),
                    persisted = address in persisted,
                    failures = failures,
                    retryInMs = retryIn.takeIf { failures > 0 && it > 0 },
                )
            },
        )
    }

    /** 交给 OkHttp 的候选：赢家最前，其余已知地址作备选，失败时 OkHttp 自己换下一条 */
    private fun routes(hostname: String, winner: InetAddress, now: Long): List<InetAddress> =
        selector.order(hostname, candidatePool(hostname, winner, now), NetworkTuning.MAX_ROUTES)

    private fun candidatePool(hostname: String, winner: InetAddress, now: Long): List<InetAddress> =
        buildList {
            add(winner)
            addAll(persistedAddresses(hostname, now))
            addAll(cachedAddresses(hostname))
        }

    private fun cachedAddresses(hostname: String): List<InetAddress> =
        sourcesFor(hostname).mapNotNull { (it as? CachingAddressSource)?.cached(hostname) }.flatten()

    private fun dohSource(name: String, url: String, bootstrap: List<String>): AddressSource {
        val source = DohSource(url, bootstrap.map { InetAddress.getByName(it) })
        return CachingAddressSource(DOH_CACHE_TTL_MS, clock, ::isUsable, name = name) { hostname ->
            source.client.lookup(hostname)
        }
    }

    private fun isUsable(hostname: String, address: InetAddress): Boolean =
        !health.isOnProbation(hostname, address)

    /** 主客户端成功完成 TLS 握手后刷新赢家和持久化记录。 */
    fun reportSuccess(hostname: String, address: InetAddress) {
        if (!isBusinessDomain(hostname)) return
        acceptWinner(hostname, address)
    }

    /** 主客户端连接失败时立即淘汰对应赢家，并按失败类型给该地址定缓刑期。 */
    fun reportFailure(hostname: String, address: InetAddress, type: FailureType) {
        if (!isBusinessDomain(hostname)) return
        winners.computeIfPresent(hostname) { _, winner ->
            if (winner.address == address) null else winner
        }
        val retryIn = health.onFailure(hostname, address, type) - clock()
        if (type == FailureType.TLS) removePersistedAddress(hostname, address)
        diagnostics.warn("degrade host=$hostname addr=${address.hostAddress} type=$type retryIn=${retryIn}ms")
    }

    /**
     * 强制下一次 lookup 跳过赢家缓存，从保留的解析缓存 + 缓刑过滤中换 IP，
     * 不重新查询 DNS。由重试拦截器在连接失败后调用。
     *
     * 解析缓存保持有效；仅当缓存内地址全部在缓刑期时，来源才会忽略缓存重新查询。
     */
    fun forceReResolve(hostname: String) {
        winners.remove(hostname)
    }

    /**
     * 一个来源的候选全部并发探测：
     * - 第一个通过的立刻返回给竞速（不拖慢请求）；
     * - 其余探测继续跑完，**通过 TLS 校验的一律入信任列表**，不只竞速赢家——
     *   多一个已验证地址，系统 DNS 被污染时就多一条退路；
     * - 全程不取消：取消会把就要通过的地址一起丢掉。
     */
    private fun probeSource(
        hostname: String,
        addresses: List<InetAddress>,
        via: String,
        probesSeen: MutableCollection<ProbeTrace>,
    ): InetAddress? {
        val candidates = addresses.distinct().filterNot { health.isOnProbation(hostname, it) }
        if (candidates.isEmpty()) return null

        val verified = CompletableFuture<InetAddress?>()
        val remaining = AtomicInteger(candidates.size)
        candidates.forEach { address ->
            probeExecutor.execute {
                val passed = probe(hostname, address, via, probesSeen)
                if (passed != null) {
                    acceptVerified(hostname, passed, via)
                    verified.complete(passed)
                }
                if (remaining.decrementAndGet() == 0) verified.complete(null)
            }
        }
        return try {
            verified.get()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } catch (e: ExecutionException) {
            null
        }
    }

    /**
     * 通过 TLS 校验的地址进信任列表（持久化）并解除缓刑。
     *
     * 刻意与 [acceptWinner] 分开：赢家要额外写 winner 缓存，而这里只负责让地址可用，
     * 所以落败来源验证通过的地址同样能进池、下次直接成为候选或备选。
     */
    private fun acceptVerified(hostname: String, address: InetAddress, via: String) {
        health.onSuccess(hostname, address)
        // 只在真正写进信任列表时记一条：否则每次解析都写，事件会被刷屏
        if (persistSuccess(hostname, address, clock())) {
            diagnostics.info("trusted add host=$hostname addr=${address.hostAddress} via=$via")
        }
    }

    private fun probe(
        hostname: String,
        address: InetAddress,
        via: String,
        probesSeen: MutableCollection<ProbeTrace>,
    ): InetAddress? {
        val startedAt = clock()
        val report = prober.probe(hostname, address)
        probesSeen += ProbeTrace(
            address = address.hostAddress.orEmpty(),
            via = via,
            outcome = when (report.outcome) {
                ProbeOutcome.OK -> "通过"
                ProbeOutcome.CONNECT_FAILED -> "连接失败"
                ProbeOutcome.TLS_FAILED -> "握手/证书失败"
            },
            tcpMs = report.tcpMs ?: (clock() - startedAt),
            tlsMs = report.tlsMs,
            detail = report.detail,
        )
        return when (report.outcome) {
            ProbeOutcome.OK -> address
            ProbeOutcome.CONNECT_FAILED -> {
                recordProbeFailure(hostname, address, FailureType.CONNECT, startedAt)
                null
            }
            ProbeOutcome.TLS_FAILED -> {
                recordProbeFailure(hostname, address, FailureType.TLS, startedAt)
                null
            }
        }
    }

    private fun acceptWinner(hostname: String, address: InetAddress) {
        val now = clock()
        health.onSuccess(hostname, address)
        winners[hostname] = WinnerEntry(address, now, now + WINNER_TTL_MS)
        persistSuccess(hostname, address, now)
        Log.d(TAG, "dns verified: $hostname -> ${address.hostAddress}")
    }

    private fun recordProbeFailure(
        hostname: String,
        address: InetAddress,
        type: FailureType,
        probeStartedAt: Long,
    ) {
        // 探测期间该地址刚被真实请求验证过：以更新鲜的成功为准，不记这次失败
        val currentWinner = winners[hostname]
        if (currentWinner?.address == address && currentWinner.verifiedAt >= probeStartedAt) return
        health.onFailure(hostname, address, type)
        winners.computeIfPresent(hostname) { _, winner ->
            if (winner.address == address) null else winner
        }
        if (type == FailureType.TLS) removePersistedAddress(hostname, address)
    }

    private fun persistedAddresses(hostname: String, now: Long): List<InetAddress> =
        readPersisted(hostname)
            .filter { now - it.succeededAt < PERSISTED_TTL_MS }
            .mapNotNull { runCatching { InetAddress.getByName(it.address) }.getOrNull() }

    /** 返回是否真的写入了（同一地址一小时内不重复写，避免每次解析都改磁盘） */
    private fun persistSuccess(hostname: String, address: InetAddress, now: Long): Boolean {
        val hostAddress = address.hostAddress ?: return false
        val current = readPersisted(hostname)
        val existing = current.firstOrNull { it.address == hostAddress }
        if (existing != null && now - existing.succeededAt < PERSIST_WRITE_INTERVAL_MS) return false
        val updated = listOf(PersistedAddress(hostAddress, now)) +
            current.filterNot { it.address == hostAddress }.take(MAX_PERSISTED_IPS - 1)
        prefs.edit().putString(persistKey(hostname), encodePersisted(updated)).apply()
        return true
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
        BUSINESS_DOMAINS.any { hostname == it || hostname.endsWith(".$it") }

    private fun persistKey(hostname: String) = "$PERSIST_PREFIX$hostname"

    private data class WinnerEntry(val address: InetAddress, val verifiedAt: Long, val expiresAt: Long)
    private data class PersistedAddress(val address: String, val succeededAt: Long)
    enum class FailureType { TLS, CONNECT, STREAM }

    internal companion object {
        /** 走 DoH 解析与地址固定的域名，含子域；pixiv 主站与图片 CDN 不同 IP 段，分开列 */
        val BUSINESS_DOMAINS = listOf("poipiku.com", "pixiv.net", "pximg.net", "pages.dev", "pic-relay.cyou")

        /** 诊断报告里逐条展示的域名，须落在 [BUSINESS_DOMAINS] 之内 */
        val BUSINESS_HOSTS = listOf(
            "poipiku.com",
            "cdn.poipiku.com",
            "www.pixiv.net",
            "i.pximg.net",
            "piku-img.pages.dev",
            "pic-relay.cyou",
        )

        /** pixiv 侧完全不走系统 DNS：污染答案是假地址，只会白花一次探测 */
        val PIXIV_DOMAINS = listOf("pixiv.net", "pximg.net")

        /** 内置固定 IP：投毒或自家域名过期时仍有一条路。改动前先实测这几个地址可用 */
        val STATIC_ADDRESSES = mapOf(
            // Cloudflare anycast，与 DoH 给的答案一致
            "www.pixiv.net" to listOf("172.64.145.17", "104.18.42.239"),
            // pixiv 自有网段，图片走清 SNI 直连
            "i.pximg.net" to listOf("210.140.139.129", "210.140.139.133", "210.140.139.134"),
            // 我们自己部署的中转（自建 DoH 与图片中转同一个域）
            "piku-img.pages.dev" to listOf("172.66.44.124", "172.66.47.132"),
            "pic-relay.cyou" to listOf("104.21.73.233", "172.67.193.18"),
        )

        const val WORKER_DOH_URL = "https://piku-img.pages.dev/dns-query"
        const val WORKER_SOURCE_NAME = "自建"
        const val STATIC_SOURCE_NAME = "内置固定 IP"

        fun isPixivDomain(hostname: String): Boolean =
            PIXIV_DOMAINS.any { hostname == it || hostname.endsWith(".$it") }

        const val TAG = "PikuDiag"
        const val DOH_TIMEOUT_MS = 5_000L
        const val PERSISTED_HEAD_START_MS = 300L

        /** 探测来源里的"持久化信任地址"这一路 */
        const val PERSISTED_VIA = "持久化信任"

        /** 系统 DNS 来源的名字：诊断判定里要区分"只有系统 DNS 有答案" */
        const val SYSTEM_SOURCE_NAME = "系统 DNS"

        const val RESOLUTION_TIMEOUT_MS = 11_000L
        const val SYSTEM_CACHE_TTL_MS = 30_000L
        const val WINNER_TTL_MS = 60_000L
        const val DOH_CACHE_TTL_MS = 10 * 60_000L
        const val PERSISTED_TTL_MS = 7 * 24 * 60 * 60_000L
        const val PERSIST_WRITE_INTERVAL_MS = 60 * 60_000L
        const val MAX_PERSISTED_IPS = 4
        const val PERSIST_PREFIX = "trusted_dns_ip_"
    }
}
