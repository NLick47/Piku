package com.piku.client.core.network.dns

import com.piku.client.core.network.tls.PoipikuNetworkPolicy
import com.piku.client.core.network.tls.SniRule
import com.piku.client.core.storage.prefs.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ao3DirectAccessTest {

    private val prefs = InMemorySharedPreferences()

    @Test
    fun ao3RuleClearsSni() {
        val rule: SniRule = PoipikuNetworkPolicy.rules.getValue("archiveofourown.org")
        assertNull("SNI 必须清空", rule.sni)
        assertTrue("证书 SAN 白名单须含主域", "archiveofourown.org" in rule.trustedSans)
        assertTrue("证书 SAN 白名单须含通配", "*.archiveofourown.org" in rule.trustedSans)
    }

    @Test
    fun ao3IsManagedByPolicy() {
        assertTrue(PoipikuNetworkPolicy.isManaged("archiveofourown.org"))
        // 子域（download.archiveofourown.org）没有自己的规则时原样发 SNI，
        // 由 DoH 的 endsWith 白名单覆盖解析，SNI 清空只对主域生效。
        assertEquals(
            "download.archiveofourown.org",
            PoipikuNetworkPolicy.sniFor("download.archiveofourown.org"),
        )
    }

    /** 咬人：若有人把 AO3 的规则删掉或改回原样发 SNI，这条会红 */
    @Test
    fun ao3SniIsNotTheHostname() {
        assertFalse(
            "AO3 不能原样发 SNI，握手会被 RST",
            PoipikuNetworkPolicy.sniFor("archiveofourown.org") == "archiveofourown.org",
        )
    }

    @Test
    fun ao3DomainIsInBusinessWhitelist() {
        assertTrue(
            "AO3 必须进 BUSINESS_DOMAINS 才会走 DoH/探测/重试",
            DoHDns.BUSINESS_DOMAINS.any { it == "archiveofourown.org" },
        )
    }

    @Test
    fun ao3HostIsListedInDiagnostics() {
        assertTrue(DoHDns.BUSINESS_HOSTS.contains("archiveofourown.org"))
        // 每个诊断域名都须落在白名单内（本身相等或是某个白名单域的子域）
        DoHDns.BUSINESS_HOSTS.forEach { host ->
            assertTrue(
                "诊断域名 $host 不在白名单内",
                DoHDns.BUSINESS_DOMAINS.any { host == it || host.endsWith(".$it") },
            )
        }
    }

    @Test
    fun ao3HasBuiltInAddresses() {
        val ips = DoHDns.STATIC_ADDRESSES.getValue("archiveofourown.org")
        assertTrue("AO3 必须有内置固定 IP", ips.isNotEmpty())
        ips.forEach { ip ->
            assertTrue("IP 格式应合法: $ip", ip.matches(Regex("""\d+\.\d+\.\d+\.\d+""")))
        }
    }

    @Test
    fun ao3ResolutionPutsStaticIpFirst() {
        // 静态 IP 打头、系统 DNS 垫底：AO3 的系统 DNS 答案是投毒的
        val names = DoHDns(prefs).sourceNamesFor("archiveofourown.org")
        assertEquals("内置固定 IP", names.first())
        assertTrue("系统 DNS 只能是最后一条退路", names.last() == "系统 DNS")
    }

    @Test
    fun otherDomainsKeepSystemDnsFirst() {
        // 未登记的域名仍按系统 DNS 打头，别被 AO3 的排序带偏
        val names = DoHDns(prefs).sourceNamesFor("example.com")
        assertEquals("系统 DNS", names.first())
    }
}
