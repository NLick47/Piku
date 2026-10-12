package com.piku.client.data.remote

import com.piku.client.data.local.InMemorySharedPreferences
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * 兜底路径的最后一环：内置 IP 进了 lookup 的返回值之后，OkHttp 是不是真的去拨它。
 *
 * 只断言"拨号落在哪个地址上"——连不通、证书不过都无所谓，那是真实请求该判的事。
 * 没网时拨号失败同样会记下地址，所以这个用例不挑网络环境。
 */
class BuiltInAddressDialTest {

    @Test
    fun theFallbackDialGoesToTheBuiltInCdnAddress() {
        val dns = DoHDns(
            InMemorySharedPreferences(),
            clock = { 1_000_000L },
            prober = AddressProbe { _, _ -> ProbeReport.connectFailed() },
            jitter = { 0L },
            // 所有来源全灭：只剩内置 IP 这条兜底
            sourcesOverride = listOf(AddressSource { emptyList() }),
        )
        val builtIn = DoHDns.STATIC_ADDRESSES.getValue("cdn.poipiku.com").map { InetAddress.getByName(it) }

        val dialed = mutableListOf<InetAddress>()
        val client = OkHttpClient.Builder()
            .dns(dns)
            .connectTimeout(DIAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .eventListener(object : EventListener() {
                override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
                    inetSocketAddress.address?.let { dialed += it }
                }
            })
            .build()

        runCatching {
            client.newCall(Request.Builder().url("https://cdn.poipiku.com/").build())
                .execute()
                .close()
        }

        assertTrue("兜底地址没走到 OkHttp，一次拨号都没有", dialed.isNotEmpty())
        assertTrue("拨号应落在内置表里，实际拨了 $dialed", dialed.all { it in builtIn })
    }

    @Test
    fun theBuiltInCdnAddressIsNotDialedWhileASourceStillAnswers() {
        val fallbackOnly = InetAddress.getByName(DoHDns.STATIC_ADDRESSES.getValue("cdn.poipiku.com").first())
        val resolved = InetAddress.getByName("127.0.0.1")
        val dns = DoHDns(
            InMemorySharedPreferences(),
            clock = { 1_000_000L },
            prober = AddressProbe { _, _ -> ProbeReport.ok() },
            jitter = { 0L },
            sourcesOverride = listOf(AddressSource { listOf(resolved) }),
        )

        val dialed = mutableListOf<InetAddress>()
        val client = OkHttpClient.Builder()
            .dns(dns)
            .connectTimeout(DIAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .eventListener(object : EventListener() {
                override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
                    inetSocketAddress.address?.let { dialed += it }
                }
            })
            .build()

        runCatching {
            client.newCall(Request.Builder().url("https://cdn.poipiku.com/").build())
                .execute()
                .close()
        }

        assertTrue("来源有答案时不该轮到内置 IP，实际拨了 $dialed", dialed.isNotEmpty() && fallbackOnly !in dialed)
    }

    private companion object {
        const val DIAL_TIMEOUT_MS = 700L
    }
}
