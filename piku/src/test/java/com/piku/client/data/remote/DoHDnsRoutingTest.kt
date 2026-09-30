package com.piku.client.data.remote

import com.piku.client.data.local.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch

class DoHDnsRoutingTest {

    private var now = 1_000_000L
    private val prefs = InMemorySharedPreferences()
    private val ipA = InetAddress.getByName("1.1.1.1")
    private val ipB = InetAddress.getByName("2.2.2.2")
    private val ipC = InetAddress.getByName("3.3.3.3")

    private fun dns(
        sources: List<AddressSource> = emptyList(),
        probe: AddressProbe = AddressProbe { _, _ -> ProbeReport.ok() },
    ) = DoHDns(
        prefs,
        clock = { now },
        prober = probe,
        jitter = { 0L },
        sourcesOverride = sources,
    )

    @Test
    fun lookupOffersAlternatesSoOkHttpCanFailOverInOneRequest() {
        val dns = dns()
        dns.reportSuccess(HOST, ipA)
        dns.reportSuccess(HOST, ipB)

        // 赢家最前，曾经验证过的地址跟在后面：同一次请求内就能换 IP，不必回到竞速
        assertEquals(listOf(ipB, ipA), dns.lookup(HOST))
    }

    @Test
    fun degradedAddressComesBackOnceItsProbationExpires() {
        val dns = dns()
        dns.reportSuccess(HOST, ipA)
        dns.reportSuccess(HOST, ipB)
        dns.reportFailure(HOST, ipA, DoHDns.FailureType.CONNECT)

        assertEquals(listOf(ipB), dns.lookup(HOST))

        // 旧实现要等 2 分钟 TTL：这段时间里"曾经可用的 IP 突然又能用"是看不到的
        now += AddressHealth.CONNECT_BASE_MS
        assertEquals(listOf(ipB, ipA), dns.lookup(HOST))
    }

    @Test
    fun successClearsProbationSoTheNextBackoffStartsFromTheBase() {
        val dns = dns()
        dns.reportSuccess(HOST, ipA)
        dns.reportFailure(HOST, ipA, DoHDns.FailureType.CONNECT)
        dns.reportFailure(HOST, ipA, DoHDns.FailureType.CONNECT)
        dns.reportSuccess(HOST, ipA)
        dns.reportFailure(HOST, ipA, DoHDns.FailureType.CONNECT)
        dns.reportSuccess(HOST, ipB)

        // 成功清零后这一次失败只缓刑 2s；若计数没清零则是 4s，此刻还看不到 ipA
        now += AddressHealth.CONNECT_BASE_MS
        assertEquals(listOf(ipB, ipA), dns.lookup(HOST))
    }

    @Test
    fun allAddressesDegradedStillOffersAPersistedOneInsteadOfUnknownHost() {
        val dns = dns(
            sources = listOf(AddressSource { listOf(ipC) }),
            probe = AddressProbe { _, _ -> ProbeReport.connectFailed() },
        )
        dns.reportSuccess(HOST, ipA)
        dns.reportSuccess(HOST, ipB)
        dns.reportFailure(HOST, ipB, DoHDns.FailureType.CONNECT)

        // 一个探测都没成功，但持久化里还有曾经验证过的地址：交给真实请求去试，
        // 不抛"域名不存在"——那会让 App 看起来像没网，用户只能重启
        val result = dns.lookup(HOST)

        assertEquals(listOf(ipB), result)
    }

    @Test
    fun unknownHostStillThrowsWhenNothingWasEverVerified() {
        val dns = dns(
            sources = listOf(AddressSource { emptyList() }),
            probe = AddressProbe { _, _ -> ProbeReport.connectFailed() },
        )

        assertThrows(UnknownHostException::class.java) { dns.lookup(HOST) }
    }

    @Test
    fun raceWinnerIsReturnedWhenNothingIsCached() {
        val dns = dns(sources = listOf(AddressSource { listOf(ipC) }))

        assertEquals(listOf(ipC), dns.lookup(HOST))
    }

    @Test
    fun switchesToTheNextAddressAndBringsItBackAfterProbation() {
        val cached = CachingAddressSource(
            ttlMs = 60_000,
            clock = { now },
            isUsable = { _, _ -> true },
            delegate = AddressSource { listOf(ipA, ipB) },
        )
        val dns = dns(
            sources = listOf(cached),
            probe = AddressProbe { _, address ->
                if (address == ipA) ProbeReport.connectFailed() else ProbeReport.ok()
            },
        )

        // 竞速里 ipA 连不上：下一个地址顶上来，而不是把这次解析判死
        assertEquals(listOf(ipB), dns.lookup(HOST))

        // 缓刑期内 ipA 不参与，哪怕它还在解析缓存里
        assertEquals(listOf(ipB), dns.lookup(HOST))

        // 缓刑到期后回到候选尾部：先健康的，再刚回来的
        now += AddressHealth.CONNECT_BASE_MS
        assertEquals(listOf(ipB, ipA), dns.lookup(HOST))
    }

    @Test
    fun candidateListIsBoundedByTheConfiguredBudget() {
        val dns = dns()
        dns.reportSuccess(HOST, ipA)
        dns.reportSuccess(HOST, ipB)
        dns.reportSuccess(HOST, ipC)

        assertEquals(NetworkTuning.MAX_ROUTES, dns.lookup(HOST).size)
    }

    @Test
    fun traceRecordsSourcesProbesAndChosenAddress() {
        val source = CachingAddressSource(
            ttlMs = 60_000,
            clock = { now },
            isUsable = { _, _ -> true },
            name = "alidns",
            delegate = AddressSource { listOf(ipA, ipB) },
        )
        val dns = dns(
            sources = listOf(source),
            // ipA 立刻失败、ipB 稍慢成功：让两条探测结果都落进链路记录
            probe = AddressProbe { _, address ->
                if (address == ipA) {
                    ProbeReport.connectFailed("ConnectException: refused")
                } else {
                    Thread.sleep(30)
                    ProbeReport.ok()
                }
            },
        )

        dns.lookup(HOST)

        val trace = dns.lastTrace(HOST)!!
        assertEquals("alidns", trace.sources.single().name)
        assertEquals(listOf(ipA.hostAddress, ipB.hostAddress), trace.sources.single().answers)
        assertEquals(2, trace.sources.single().candidates)
        assertEquals(ipB.hostAddress, trace.winner)
        assertEquals(listOf("alidns"), winnerSourcesOf(trace))
        assertEquals(listOf(ipB.hostAddress), trace.routed)
        assertFalse(trace.fallback)

        val failed = trace.probes.single { it.address == ipA.hostAddress }
        assertEquals("连接失败", failed.outcome)
        assertEquals("ConnectException: refused", failed.detail)

        val passed = trace.probes.single { it.address == ipB.hostAddress }
        assertEquals("通过", passed.outcome)
    }

    @Test
    fun fallbackPathIsMarkedInTheTrace() {
        val dns = dns(
            sources = listOf(AddressSource { listOf(ipC) }),
            probe = AddressProbe { _, _ -> ProbeReport.connectFailed() },
        )
        dns.reportSuccess(HOST, ipA)
        dns.reportFailure(HOST, ipA, DoHDns.FailureType.CONNECT)

        runCatching { dns.lookup(HOST) }

        val trace = dns.lastTrace(HOST)!!
        assertTrue(trace.fallback)
        assertEquals(null, trace.winner)
        assertEquals(listOf(ipA.hostAddress), trace.routed)
    }

    @Test
    fun verifiedAddressesFromEverySourceEnterTheTrustedList() {
        val systemLike = CachingAddressSource(
            ttlMs = 60_000,
            clock = { now },
            isUsable = { _, _ -> true },
            name = DoHDns.SYSTEM_SOURCE_NAME,
            delegate = AddressSource { listOf(ipA) },
        )
        val dohLike = CachingAddressSource(
            ttlMs = 60_000,
            clock = { now },
            isUsable = { _, _ -> true },
            name = "alidns",
            delegate = AddressSource { listOf(ipC) },
        )
        val dns = dns(
            sources = listOf(systemLike, dohLike),
            // 系统 DNS 的地址先通过；DoH 的慢一点，输掉竞速但仍应被验证并入池
            probe = AddressProbe { _, address ->
                if (address == ipC) {
                    Thread.sleep(60)
                }
                ProbeReport.ok()
            },
        )

        dns.lookup(HOST)

        // 它在解析缓存里立刻可见，所以等到"被验证并持久化"才算数
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline &&
            dns.status(HOST).addresses.none { it.address == ipC.hostAddress && it.persisted }
        ) {
            Thread.sleep(20)
        }
        val pooled = dns.status(HOST).addresses.singleOrNull { it.address == ipC.hostAddress }

        assertTrue("DoH 验证通过的地址也要入池", pooled != null)
        assertTrue("入池后应写进持久化信任列表", pooled!!.persisted)
    }

    @Test
    fun lateSourceLandsInTheTraceAfterTheWinnerIsAccepted() {
        val gate = CountDownLatch(1)
        val system = CachingAddressSource(
            ttlMs = 60_000,
            clock = { now },
            isUsable = { _, _ -> true },
            name = DoHDns.SYSTEM_SOURCE_NAME,
            delegate = AddressSource { listOf(ipA) },
        )
        val doh = CachingAddressSource(
            ttlMs = 60_000,
            clock = { now },
            isUsable = { _, _ -> true },
            name = "alidns",
            delegate = AddressSource {
                gate.await()
                listOf(ipA, ipB)
            },
        )
        val dns = dns(sources = listOf(system, doh))

        assertEquals(listOf(ipA), dns.lookup(HOST))

        // 赢家已定而 DoH 还没回来：此刻链路里只有系统 DNS 一路
        assertEquals(listOf(DoHDns.SYSTEM_SOURCE_NAME), dns.lastTrace(HOST)!!.sources.map { it.name })

        gate.countDown()

        // 迟到的结论要落进同一份链路记录，而不是只在事件流里闪一下
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline &&
            dns.lastTrace(HOST)!!.probes.none { it.address == ipB.hostAddress }
        ) {
            Thread.sleep(10)
        }
        val trace = dns.lastTrace(HOST)!!

        assertEquals(listOf(DoHDns.SYSTEM_SOURCE_NAME, "alidns"), trace.sources.map { it.name })
        assertEquals(
            listOf(ipA.hostAddress, ipB.hostAddress),
            trace.sources.single { it.name == "alidns" }.answers,
        )
        assertTrue(trace.probes.any { it.address == ipB.hostAddress && it.via == "alidns" })
        // 采用一栏同样是最终状态：DoH 也给出了赢家地址
        assertEquals(listOf(DoHDns.SYSTEM_SOURCE_NAME, "alidns"), winnerSourcesOf(trace))
    }

    @Test
    fun pixivNeverConsultsTheSystemDnsWhilePoipikuStillLeadsWithIt() {
        val dns = dns()

        // 系统 DNS 对 pixiv 只会给投毒答案，问了等于白花一次探测
        assertFalse(DoHDns.SYSTEM_SOURCE_NAME in dns.sourceNamesFor("www.pixiv.net"))
        assertFalse(DoHDns.SYSTEM_SOURCE_NAME in dns.sourceNamesFor("i.pximg.net"))
        assertTrue(DoHDns.STATIC_SOURCE_NAME in dns.sourceNamesFor("www.pixiv.net"))
        assertEquals(DoHDns.SYSTEM_SOURCE_NAME, dns.sourceNamesFor("poipiku.com").first())
    }

    @Test
    fun builtInAddressesCoverOurOwnHostsSoPoisonedOrExpiredDnsCannotStrandUs() {
        val hosts = listOf("www.pixiv.net", "i.pximg.net")

        hosts.forEach { host ->
            val ips = DoHDns.STATIC_ADDRESSES[host]
            assertTrue("$host 缺一条内置 IP", !ips.isNullOrEmpty())
            // 必须是 IP 字面量：写成域名的话这条路照样要解析，等于没兜底
            ips!!.forEach { assertEquals(it, InetAddress.getByName(it).hostAddress) }
            assertTrue(DoHDns.BUSINESS_DOMAINS.any { host == it || host.endsWith(".$it") })
        }
        assertTrue(DoHDns.STATIC_ADDRESSES["pic-relay.cyou"].isNullOrEmpty())
        assertTrue(DoHDns.BUSINESS_DOMAINS.contains("pic-relay.cyou"))
    }

    @Test
    fun staticSourceAnswersOnlyTheHostsItHas() {
        val source = StaticAddressSource()

        assertTrue(source.resolve("i.pximg.net").isNotEmpty())
        assertTrue(source.resolve("poipiku.com").isEmpty())
    }

    @Test
    fun lastResortSourceIsAskedAgainAfterEveryOtherSourceFailed() {
        var workerCalls = 0
        val silent = AddressSource { emptyList() }
        val worker = CachingAddressSource(
            ttlMs = 60_000,
            clock = { now },
            isUsable = { _, _ -> true },
            name = DoHDns.WORKER_SOURCE_NAME,
            delegate = AddressSource {
                workerCalls++
                if (workerCalls == 1) emptyList() else listOf(ipC)
            },
        )
        val dns = dns(
            sources = listOf(silent, worker),
            probe = AddressProbe { _, address ->
                if (address == ipC) ProbeReport.ok() else ProbeReport.connectFailed()
            },
        )

        // 竞速里自建 DoH 第一次没给答案：全灭后必须再问它一次，而不是直接判"域名不存在"
        assertEquals(listOf(ipC), dns.lookup(HOST))
        assertTrue("自建 DoH 应被问第二次", workerCalls >= 2)
    }

    private companion object {
        const val HOST = "poipiku.com"
    }
}
