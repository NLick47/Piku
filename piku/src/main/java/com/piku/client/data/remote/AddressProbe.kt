package com.piku.client.data.remote

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/** 探测结论：连接阶段与 TLS 阶段要分开，两者的缓刑起点不同 */
internal enum class ProbeOutcome { OK, CONNECT_FAILED, TLS_FAILED }

/**
 * 一次探测的完整结果：两个阶段各自耗时，失败时带异常摘要。
 *
 * 分阶段是为了能区分两类完全不同的故障：TCP 就通不了（该 IP 被墙 / DoH 给的地址不可达），
 * 与 TCP 通了但握手不过（SNI 阻断、中间设备重置、证书不匹配）——处置方式不一样。
 */
internal data class ProbeReport(
    val outcome: ProbeOutcome,
    /** TCP 连接耗时；连接没成功时为 null */
    val tcpMs: Long? = null,
    /** TLS 握手 + 证书校验耗时；没走到这一步时为 null */
    val tlsMs: Long? = null,
    /** 失败原因摘要（异常类名 + message / 证书校验未通过） */
    val detail: String? = null,
) {
    companion object {
        fun ok() = ProbeReport(ProbeOutcome.OK)

        fun connectFailed(detail: String? = null) =
            ProbeReport(ProbeOutcome.CONNECT_FAILED, detail = detail)

        fun tlsFailed(detail: String? = null) = ProbeReport(ProbeOutcome.TLS_FAILED, detail = detail)
    }
}

/** 地址可用性探测：生产实现是真实 TCP + TLS 握手，单测注入假实现即可绕开网络 */
internal fun interface AddressProbe {
    fun probe(hostname: String, address: InetAddress): ProbeReport
}

/** 只有完成真实 TLS 握手与证书校验的地址才算可用 */
internal class TlsAddressProbe(
    private val socketFactory: SniStrippingSocketFactory = SniStrippingSocketFactory(),
    private val hostnameVerifier: PoipikuHostnameVerifier = PoipikuHostnameVerifier(),
    private val now: () -> Long = System::currentTimeMillis,
) : AddressProbe {

    override fun probe(hostname: String, address: InetAddress): ProbeReport {
        val rawSocket = Socket()
        try {
            val connectStartedAt = now()
            try {
                rawSocket.connect(InetSocketAddress(address, HTTPS_PORT), TCP_PROBE_TIMEOUT_MS)
            } catch (e: Exception) {
                return ProbeReport.connectFailed(detail = summarize(e))
            }
            val tcpMs = now() - connectStartedAt

            val handshakeStartedAt = now()
            try {
                val sslSocket = socketFactory.createSocket(rawSocket, hostname, HTTPS_PORT, true) as SSLSocket
                sslSocket.use {
                    it.soTimeout = TLS_HANDSHAKE_TIMEOUT_MS
                    it.startHandshake()
                    if (!hostnameVerifier.verify(hostname, it.session)) {
                        return ProbeReport(
                            ProbeOutcome.TLS_FAILED,
                            tcpMs = tcpMs,
                            tlsMs = now() - handshakeStartedAt,
                            detail = "证书校验未通过",
                        )
                    }
                }
            } catch (e: SSLException) {
                return ProbeReport(
                    ProbeOutcome.TLS_FAILED,
                    tcpMs = tcpMs,
                    tlsMs = now() - handshakeStartedAt,
                    detail = summarize(e),
                )
            } catch (e: Exception) {
                return ProbeReport.connectFailed(detail = summarize(e))
            }
            return ProbeReport(
                ProbeOutcome.OK,
                tcpMs = tcpMs,
                tlsMs = now() - handshakeStartedAt,
            )
        } finally {
            runCatching { rawSocket.close() }
        }
    }

    private fun summarize(e: Exception): String = e.describeChain(levels = 2, maxChars = MAX_DETAIL_CHARS)

    private companion object {
        const val HTTPS_PORT = 443
        const val TCP_PROBE_TIMEOUT_MS = 3_000
        const val TLS_HANDSHAKE_TIMEOUT_MS = 2_000
        const val MAX_DETAIL_CHARS = 80
    }
}
