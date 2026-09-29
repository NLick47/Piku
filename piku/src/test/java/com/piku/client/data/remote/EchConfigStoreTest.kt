package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchConfigStoreTest {

    /** 2026-09-29 本机从 AliDNS /resolve 取到的真实应答 */
    private val aliDnsRecord = """
        {"Status":0,"TC":false,"RD":true,"RA":true,"AD":false,"CD":false,
        "Question":{"name":"cloudflare-ech.com.","type":65},
        "Answer":[{"name":"cloudflare-ech.com.","TTL":235,"type":65,
        "data":"1 . alpn=\"h3,h2\" ipv4hint=\"104.18.10.118,104.18.11.118\" ech=\"AEX+DQBBOQAgACAI1UBELWGTFMIfnYHINDUpGpjVC7f/gCzU8qcW19WbSwAEAAEAAQASY2xvdWRmbGFyZS1lY2guY29tAAA=\" ipv6hint=\"2606:4700::6812:a76\""}]}
    """.trimIndent()

    /** Cloudflare 的 dns-json 给的是不带引号的形式 */
    private val cloudflareRecord = aliDnsRecord.replace("ech=\"", "ech=").replace("AAA=\"", "AAA=")

    @Test
    fun parsesAliDnsQuotedEchAndTtl() {
        val parsed = parseEchRecord(aliDnsRecord)

        assertNotNull(parsed)
        val (config, ttlMs) = parsed!!
        assertEquals(235_000L, ttlMs)
        // ECHConfigList 头两字节是列表总长：base64 解码错位这里立刻对不上
        val declared = ((config[0].toInt() and 0xFF) shl 8) or (config[1].toInt() and 0xFF)
        assertEquals(config.size - 2, declared)
        assertTrue(config.size > 60)
    }

    @Test
    fun parsesCloudflareUnquotedFormAsWell() {
        val parsed = parseEchRecord(cloudflareRecord)

        assertEquals(parseEchRecord(aliDnsRecord)!!.first.toList(), parsed!!.first.toList())
    }

    @Test
    fun rejectsResponseWithoutEchOrWithBrokenData() {
        assertNull(parseEchRecord("""{"Status":0,"Answer":[]}"""))
        // 长度前缀和实际字节数不符：宁可不启用，也不能拿半截配置去握手
        assertNull(parseEchRecord("""{"Answer":[{"TTL":60,"data":"ech=AAAA"}]}"""))
    }


    @Test
    fun waitsForTheFirstFetchWhenNothingIsCached() {
        // 冷启动：缓存空，取配置要走网络——第一次请求必须等得到，否则 pixiv 直接不可用
        val store = EchConfigStore(fetcher = {
            Thread.sleep(150)
            aliDnsRecord
        })

        val config = store.currentOrFetch(3_000)

        assertNotNull("第一次请求应当拿到 ECH 配置", config)
        val declared = ((config!![0].toInt() and 0xFF) shl 8) or (config[1].toInt() and 0xFF)
        assertEquals(config.size - 2, declared)
    }

    @Test
    fun givesUpWhenTheFetchIsSlowerThanTheBudget() {
        val store = EchConfigStore(fetcher = {
            Thread.sleep(600)
            aliDnsRecord
        })

        assertNull("超时就不该再挂着，交给调用方报错", store.currentOrFetch(50))
    }

    @Test
    fun cachedConfigIsReusedWithoutTouchingTheNetwork() {
        var fetches = 0
        val store = EchConfigStore(fetcher = {
            fetches++
            aliDnsRecord
        })

        val first = store.currentOrFetch(2_000)
        assertNotNull(first)
        val afterFirst = fetches

        assertNotNull("缓存命中要能直接拿到", store.current())
        assertTrue(store.isFresh())
        assertEquals("缓存命中不该再打网络", afterFirst, fetches)
    }

    @Test
    fun currentDoesNotRecurseIntoIsFresh() {
        // 老实现里 current() 调 isFresh()、isFresh() 又调 current()，第一次请求就 StackOverflow
        val store = EchConfigStore(fetcher = {
            Thread.sleep(200) // 让后台刷新别在断言前把缓存填上
            aliDnsRecord
        })

        assertNull("缓存空时就是 null，不能递归", store.current())
        assertFalse(store.isFresh())
    }
}
