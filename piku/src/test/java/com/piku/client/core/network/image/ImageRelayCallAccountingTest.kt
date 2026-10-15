package com.piku.client.core.network.image

import com.piku.client.core.model.ImageRouteMode
import com.piku.client.core.network.diag.NetworkDiagnostics
import com.piku.client.core.network.dns.AddressProbe
import com.piku.client.core.network.dns.AddressSource
import com.piku.client.core.network.dns.DoHDns
import com.piku.client.core.network.dns.DoHEventListener
import com.piku.client.core.network.dns.ProbeReport
import com.piku.client.core.network.http.NetworkRuntime
import com.piku.client.core.storage.prefs.InMemorySharedPreferences
import com.piku.client.core.storage.prefs.SettingsRepository
import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class ImageRelayCallAccountingTest {

    private val upstreamHost = ImageUpstream.POIPIKU.host

    private val relayHost = "relay.pic-relay.cyou"
    private val relayIp = InetAddress.getByName("127.0.0.1")
    private val upstreamIp = InetAddress.getByName("127.0.0.2")

    private val now = 1_000_000L
    private val prefs = InMemorySharedPreferences()
    private val settings = SettingsRepository(InMemorySharedPreferences())
    private val runtime = NetworkRuntime()
    private val diagnostics = NetworkDiagnostics(runtime)
    private val dns = DoHDns(
        prefs,
        clock = { now },
        prober = AddressProbe { _, _ -> ProbeReport.ok() },
        jitter = { 0L },
        diagnostics = diagnostics,
        sourcesOverride = listOf(
            AddressSource { hostname -> if (hostname == relayHost) listOf(relayIp) else listOf(upstreamIp) },
        ),
    )

    private lateinit var server: HttpServer
    private var port = 0

    private val controller = ImageRouteController(settings, prefs, runtime, listOf(relayHost))

    private val hostsSeen = CopyOnWriteArrayList<String>()

    @Before
    fun startRelayServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            hostsSeen += exchange.requestHeaders.getFirst("Host").orEmpty()
            val body = byteArrayOf(1, 2, 3)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        port = server.address.port
        settings.setImageRouteMode(ImageRouteMode.RELAY)
    }

    @After
    fun stopRelayServer() = server.stop(0)

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .dns(dns)
        .eventListenerFactory {
            DoHEventListener(dns, diagnostics) { host ->
                ImageUpstream.of(host)?.let(controller::useRelay) == true
            }
        }
        .addInterceptor(ImageRelayInterceptor(controller))
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private fun imageUrl(port: Int) = "http://$upstreamHost:$port/assets/img/a.png"

    private fun persistedUpstream(): String? = prefs.getString("trusted_dns_ip_$upstreamHost", null)

    @Test
    fun relayedSuccessDoesNotClaimTheUpstreamAddress() {
        client().newCall(Request.Builder().url(imageUrl(port)).build()).execute().use { response ->
            assertEquals(200, response.code)
        }

        assertTrue("中继没被走到，断言会空跑：$hostsSeen", hostsSeen.any { it.startsWith(relayHost) })
        val winner = dns.status(upstreamHost).winner
        assertNull("上游赢家被写成了中继地址 $winner（应为空）", winner)
        assertNull("上游持久化信任被写进了 ${persistedUpstream()}", persistedUpstream())
    }

    @Test
    fun relayedConnectFailureIsNotChargedToTheUpstream() {
        val deadPort = ServerSocket(0).use { it.localPort }
        val failure = runCatching {
            client().newCall(Request.Builder().url(imageUrl(deadPort)).build()).execute().close()
        }.exceptionOrNull()
        assertTrue("应当连不上：$failure", failure is IOException)

        val pool = dns.status(upstreamHost).addresses.map { it.address }
        assertTrue("中继地址 ${relayIp.hostAddress} 被记进了上游候选池：$pool", pool.none { it == relayIp.hostAddress })
    }
}
