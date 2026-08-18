package com.piku.client.data.remote

import com.piku.client.data.local.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class DoHDnsTest {

    private val prefs = InMemorySharedPreferences()
    private val dns = DoHDns(prefs)

    private val ipA = InetAddress.getByName("1.1.1.1")
    private val ipB = InetAddress.getByName("2.2.2.2")
    private val ipC = InetAddress.getByName("3.3.3.3")
    private val ipD = InetAddress.getByName("4.4.4.4")
    private val ipE = InetAddress.getByName("5.5.5.5")

    private fun persisted(): String? =
        prefs.getString("trusted_dns_ip_poipiku.com", null)

    @Test
    fun reportSuccessPersistsAddress() {
        dns.reportSuccess("poipiku.com", ipA)

        val line = persisted()
        assertTrue(line != null && line.startsWith("1.1.1.1|"))
    }

    @Test
    fun reportFailureTlsRemovesPersistedAddress() {
        dns.reportSuccess("poipiku.com", ipA)
        dns.reportFailure("poipiku.com", ipA, DoHDns.FailureType.TLS)

        assertFalse(persisted()?.contains("1.1.1.1") ?: true)
    }

    @Test
    fun reportFailureConnectKeepsPersistedAddress() {
        dns.reportSuccess("poipiku.com", ipA)
        dns.reportFailure("poipiku.com", ipA, DoHDns.FailureType.CONNECT)

        assertTrue(persisted()?.startsWith("1.1.1.1|") ?: false)
    }

    @Test
    fun reportFailureStreamKeepsPersistedAddress() {
        dns.reportSuccess("poipiku.com", ipA)
        dns.reportFailure("poipiku.com", ipA, DoHDns.FailureType.STREAM)

        assertTrue(persisted()?.startsWith("1.1.1.1|") ?: false)
    }

    @Test
    fun reportFailureTlsThenSuccessRepersistsAddress() {
        dns.reportSuccess("poipiku.com", ipA)
        dns.reportFailure("poipiku.com", ipA, DoHDns.FailureType.TLS)
        dns.reportSuccess("poipiku.com", ipA)

        assertTrue(persisted()?.startsWith("1.1.1.1|") ?: false)
    }

    @Test
    fun persistsAtMostFourAddresses() {
        dns.reportSuccess("poipiku.com", ipA)
        dns.reportSuccess("poipiku.com", ipB)
        dns.reportSuccess("poipiku.com", ipC)
        dns.reportSuccess("poipiku.com", ipD)
        dns.reportSuccess("poipiku.com", ipE)

        val lines = persisted()?.lines().orEmpty()
        assertEquals(4, lines.size)
        assertTrue(lines.any { it.startsWith("5.5.5.5|") })
        assertFalse(lines.any { it.startsWith("1.1.1.1|") })
    }

    @Test
    fun nonBusinessDomainIgnored() {
        dns.reportSuccess("github.com", ipA)
        dns.reportFailure("github.com", ipA, DoHDns.FailureType.TLS)

        assertFalse(persisted()?.contains("1.1.1.1") ?: false)
    }

    @Test
    fun forceReResolveDoesNotTouchPersisted() {
        dns.reportSuccess("poipiku.com", ipA)
        dns.forceReResolve("poipiku.com")

        assertTrue(persisted()?.startsWith("1.1.1.1|") ?: false)
    }
}