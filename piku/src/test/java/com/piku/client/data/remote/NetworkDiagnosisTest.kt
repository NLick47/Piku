package com.piku.client.data.remote

import com.piku.client.data.local.InMemorySharedPreferences
import com.piku.client.data.local.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import okhttp3.Call
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch

class NetworkDiagnosisTest {

    private var now = 1_000_000L
    private val prefs = InMemorySharedPreferences()
    private val ipA = InetAddress.getByName("1.1.1.1")
    private val ipB = InetAddress.getByName("2.2.2.2")
    private val runtime = NetworkRuntime(now = { now }, sleeper = {})
    private val diagnostics = NetworkDiagnostics(runtime)
    private val environment = NetworkEnvironment(
        transport = "WIFI",
        validated = true,
        metered = false,
        systemDns = listOf("192.168.1.1"),
    )

    private fun dns(probe: AddressProbe) = DoHDns(
        prefs,
        clock = { now },
        prober = probe,
        jitter = { 0L },
        sourcesOverride = listOf(
            CachingAddressSource(
                ttlMs = 60_000,
                clock = { now },
                isUsable = { _, _ -> true },
                name = "alidns",
                delegate = AddressSource { listOf(ipA, ipB) },
            ),
        ),
    )

    private val settings = SettingsRepository(InMemorySharedPreferences())
    private val routeController = ImageRouteController(settings, prefs, runtime)
    private val imageProbe = ImageRouteProbe(
        client = object : Call.Factory {
            override fun newCall(request: Request): Call =
                FakeCall(request, executeResult = { okResponse(request) })
        },
        controller = routeController,
        runtime = runtime,
    )

    private val imageDiagnostics = ImageDiagnostics(runtime)

    private fun diagnosis(dns: DoHDns, probe: AddressProbe) = NetworkDiagnosis(
        dns,
        diagnostics,
        probe,
        runtime,
        imageProbe,
        routeController,
        imageDiagnostics,
    )

    private fun trace(probes: List<ProbeTrace>) = ResolveTrace(
        hostname = "poipiku.com",
        elapsedMs = 10,
        sources = emptyList(),
        probes = probes,
        winner = null,
        routed = emptyList(),
        fallback = false,
    )

    @Test
    fun reportRendersTheWholeResolutionChain() {
        val probe = AddressProbe { _, address ->
            if (address == ipA) {
                ProbeReport.connectFailed("ConnectException: refused")
            } else {
                // 慢一点，让失败那条的探测结果也落进链路记录
                Thread.sleep(30)
                ProbeReport(ProbeOutcome.OK, tcpMs = 42, tlsMs = 180)
            }
        }
        val dns = dns(probe)
        dns.lookup(HOST)

        val report = diagnosis(dns, probe).report("9.9.9", environment, live = false)

        assertTrue(report.contains("网络 WIFI（已验证）"))
        assertTrue(report.contains("系统 DNS 192.168.1.1"))
        assertTrue(report.contains("—— 解析链路"))
        assertTrue(report.contains("alidns  2 条"))
        assertTrue(report.contains("连接失败（ConnectException: refused）"))
        assertTrue(report.contains("TCP 42ms  TLS 180ms  通过"))
        assertTrue(report.contains("采用 ${ipB.hostAddress}（来自 alidns）"))
        assertTrue(report.contains("交给 OkHttp ${ipB.hostAddress}"))
        // 有可用地址时不报"问题判定"，免得风声鹤唳
        assertFalse(report.contains("判定"))
    }

    @Test
    fun reportFlagsWhenEveryCandidateIsUnreachable() {
        val probe = AddressProbe { _, _ -> ProbeReport.connectFailed("SocketTimeoutException") }
        val dns = dns(probe)
        runCatching { dns.lookup(HOST) }

        val report = diagnosis(dns, probe).report("9.9.9", environment.copy(metered = true), live = false)

        assertTrue(report.contains("判定"))
        assertTrue(report.contains("全部连不上"))
        assertTrue(report.contains("计费"))
    }

    @Test
    fun reportShowsWhyADohSourceFailed() {
        val dns = DoHDns(
            prefs,
            clock = { now },
            prober = AddressProbe { _, _ -> ProbeReport.ok() },
            jitter = { 0L },
            sourcesOverride = listOf(
                CachingAddressSource(
                    ttlMs = 60_000,
                    clock = { now },
                    isUsable = { _, _ -> true },
                    name = DoHDns.SYSTEM_SOURCE_NAME,
                    delegate = AddressSource { listOf(ipA) },
                ),
                CachingAddressSource(
                    ttlMs = 60_000,
                    clock = { now },
                    isUsable = { _, _ -> true },
                    name = "cloudflare",
                    delegate = AddressSource {
                        throw UnknownHostException("poipiku.com").apply {
                            initCause(java.io.IOException("response: 400 Bad Request"))
                        }
                    },
                ),
            ),
        )
        runCatching { dns.lookup(HOST) }
        val probe = AddressProbe { _, _ -> ProbeReport.ok() }

        val report = diagnosis(dns, probe).report("9.9.9", environment, live = false)

        assertTrue(report.contains("cloudflare  0 条"))
        assertTrue(
            report.contains(
                "查询失败（UnknownHostException: poipiku.com ← IOException: response: 400 Bad Request）",
            ),
        )
        assertTrue(report.contains("只靠系统 DNS"))
    }

    @Test
    fun reportShowsTheImageRouteDecisionAndRelayHealth() {
        routeController.applyProbe(
            ImageProbeResult(
                ok = false,
                elapsedMs = 312,
                statusCode = null,
                error = "SocketTimeoutException: connect timed out",
                atMillis = now,
            ),
        )
        routeController.relayHealth.onFailure(ImageRelayInterceptor.RELAY_HOSTS[0])
        routeController.relayHealth.onSuccess(ImageRelayInterceptor.RELAY_HOSTS[1])
        val dns = dns(AddressProbe { _, _ -> ProbeReport.ok() })
        runCatching { dns.lookup(HOST) }

        val report = diagnosis(dns, AddressProbe { _, _ -> ProbeReport.ok() })
            .report("9.9.9", environment, live = false)

        assertTrue(report.contains("—— 图片线路 ——"))
        assertTrue(report.contains("模式 自动（AUTO）   当前 走中转"))
        assertTrue(report.contains("最近判定 切到中转｜启动探测：直连不可用｜"))
        assertTrue(report.contains("启动探测 直连不可用｜312ms（SocketTimeoutException: connect timed out）"))
        assertTrue(report.contains("${ImageRelayInterceptor.RELAY_HOSTS[1]}  第 1 位  健康"))
        assertTrue(report.contains("${ImageRelayInterceptor.RELAY_HOSTS[0]}  第 2 位  健康（累计失败 1 次）"))
    }

    @Test
    fun liveReportProbesDirectAndEveryRelay() {
        val probe = AddressProbe { _, _ -> ProbeReport.ok() }
        val dns = dns(probe)

        val report = diagnosis(dns, probe).report("9.9.9", environment, live = true)

        assertTrue(report.contains("—— 图片线路（实时探测）——"))
        assertTrue(report.contains("直连 ${ImageRelayInterceptor.CDN_HOST}  可用"))
        assertTrue(report.contains("中转 ${ImageRelayInterceptor.RELAY_HOSTS[0]}  可用"))
        assertTrue(report.contains("中转 ${ImageRelayInterceptor.RELAY_HOSTS[1]}  可用"))
    }

    @Test
    fun cancelledSourceIsNotReportedAsDohFailure() {
        val dns = DoHDns(
            prefs,
            clock = { now },
            prober = AddressProbe { _, _ -> ProbeReport.ok() },
            jitter = { 0L },
            sourcesOverride = listOf(
                CachingAddressSource(
                    ttlMs = 60_000,
                    clock = { now },
                    isUsable = { _, _ -> true },
                    name = DoHDns.SYSTEM_SOURCE_NAME,
                    delegate = AddressSource { listOf(ipA) },
                ),
                CachingAddressSource(
                    ttlMs = 60_000,
                    clock = { now },
                    isUsable = { _, _ -> true },
                    name = "alidns",
                    delegate = AddressSource {
                        // 竞速取消的真实形状：OkHttp 把线程中断包成 UnknownHostException(域名)
                        throw UnknownHostException("poipiku.com").apply { initCause(InterruptedException()) }
                    },
                ),
            ),
        )
        runCatching { dns.lookup(HOST) }
        val probe = AddressProbe { _, _ -> ProbeReport.ok() }

        val report = diagnosis(dns, probe).report("9.9.9", environment, live = false)

        assertTrue(report.contains("alidns  0 条"))
        assertTrue(report.contains("已取消（其他来源先返回，本次没有结论）"))
        assertFalse(report.contains("interruptedException"))
        assertFalse(report.contains("只靠系统 DNS"))
    }

    @Test
    fun reportShowsASourceThatAnsweredAfterTheWinnerWasAccepted() {
        val gate = CountDownLatch(1)
        val dns = DoHDns(
            prefs,
            clock = { now },
            prober = AddressProbe { _, _ -> ProbeReport.ok() },
            jitter = { 0L },
            sourcesOverride = listOf(
                CachingAddressSource(
                    ttlMs = 60_000,
                    clock = { now },
                    isUsable = { _, _ -> true },
                    name = DoHDns.SYSTEM_SOURCE_NAME,
                    delegate = AddressSource { listOf(ipA) },
                ),
                CachingAddressSource(
                    ttlMs = 60_000,
                    clock = { now },
                    isUsable = { _, _ -> true },
                    name = "alidns",
                    delegate = AddressSource {
                        gate.await()
                        listOf(ipB)
                    },
                ),
            ),
        )
        val probe = AddressProbe { _, _ -> ProbeReport.ok() }
        dns.lookup(HOST)
        gate.countDown()

        // 打开诊断时迟到的 DoH 已经回来：链路里要能看到它给出的地址，不是只在事件流里
        val deadline = System.currentTimeMillis() + 2_000
        var report = diagnosis(dns, probe).report("9.9.9", environment, live = false)
        while (System.currentTimeMillis() < deadline && !report.contains("alidns  1 条")) {
            Thread.sleep(10)
            report = diagnosis(dns, probe).report("9.9.9", environment, live = false)
        }

        assertTrue(report.contains("alidns  1 条"))
        assertTrue(report.contains(ipB.hostAddress))
        // 赢家不是 DoH 给的，采用一栏不该把它算成赢家来源
        assertTrue(report.contains("采用 ${ipA.hostAddress}（来自 ${DoHDns.SYSTEM_SOURCE_NAME}）"))
    }

    @Test
    fun reportShowsTheEventSectionEvenWhenThereIsNothingToClear() {
        val probe = AddressProbe { _, _ -> ProbeReport.ok() }
        val dns = dns(probe)

        val report = diagnosis(dns, probe).report("9.9.9", environment, live = false)

        assertTrue(report.contains("—— 事件（最新在前，共 0 条）——"))
        assertTrue(report.contains("清空只清这里"))
    }

    @Test
    fun reportShowsImageNetworkAttempts() {
        imageDiagnostics.recordAttempt(
            host = "pic-relay.cyou",
            path = "/img/12345_360.jpg",
            outcome = ImageDiagnostics.Outcome.HANDSHAKE_FAILED,
            detail = "SSLException: Connection reset",
            elapsedMs = 218,
            relay = true,
        )
        imageDiagnostics.recordAttempt(
            host = ImageRelayInterceptor.CDN_HOST,
            path = "/img/67890_360.jpg",
            outcome = ImageDiagnostics.Outcome.CONNECT_FAILED,
            detail = "SocketTimeoutException",
            elapsedMs = 5_001,
            relay = false,
        )
        val probe = AddressProbe { _, _ -> ProbeReport.ok() }
        val dns = dns(probe)

        val report = diagnosis(dns, probe).report("9.9.9", environment, live = false)

        assertTrue(report.contains("—— 图片请求（网络层，本次进程）——"))
        assertTrue(report.contains("请求 2 次：成功 0  失败 2  取消 0"))
        assertTrue(report.contains("线路：直连 1  中转 1（pic-relay.cyou 1）"))
        assertTrue(report.contains("中转 pic-relay.cyou/img/12345_360.jpg"))
        assertTrue(report.contains("握手/连接被重置（常见于 SNI 阻断）  218ms"))
        assertTrue(report.contains("直连 ${ImageRelayInterceptor.CDN_HOST}/img/67890_360.jpg"))
    }

    @Test
    fun verdictSeparatesHandshakeFailureFromUnreachableAddresses() {
        val handshake = trace(
            listOf(ProbeTrace(ipA.hostAddress, "alidns", "握手/证书失败", 30, 120, "SSLHandshakeException: reset")),
        )
        val unreachable = trace(
            listOf(ProbeTrace(ipA.hostAddress, "alidns", "连接失败", 3_001, null, "SocketTimeoutException")),
        )

        assertTrue(verdictOf(handshake)!!.contains("SNI"))
        assertTrue(verdictOf(unreachable)!!.contains("全部连不上"))
    }

    @Test
    fun verdictIsSilentWhenEverythingWorks() {
        val healthy = trace(listOf(ProbeTrace(ipA.hostAddress, "alidns", "通过", 30, 120, null)))
            .copy(winner = ipA.hostAddress)

        assertEquals(null, verdictOf(healthy))
    }

    private companion object {
        const val HOST = "poipiku.com"
    }
}
