package com.piku.client.data.remote

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.nio.channels.SocketChannel
import javax.net.ssl.HandshakeCompletedListener
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * 按 [PoipikuNetworkPolicy] 改写 TLS ClientHello 的 SNI 扩展。
 *
 * 部分网络环境会对特定 SNI 明文的 TLS 握手直接 RST：
 * - poipiku.com：清空 SNI（服务器按 IP 提供证书，不依赖 SNI）
 * - cdn.poipiku.com：伪装成其 CNAME 目标（CloudFront 按 SNI 选分发，
 *   伪装域名就是同一分发，证书为 *.cloudfront.net）
 *
 * SNI 改写后证书 SAN 与 URL 域名不再一致，需配合 [PoipikuHostnameVerifier]。
 */
class SniStrippingSocketFactory : SSLSocketFactory() {

    private val trustManager: X509TrustManager = run {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as java.security.KeyStore?)
        tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private val sslContext: SSLContext = run {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }
    }

    private val delegate: SSLSocketFactory = sslContext.socketFactory

    fun trustManager(): X509TrustManager = trustManager

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket =
        SniStrippingSocket(
            delegate.createSocket(socket, PoipikuNetworkPolicy.sniFor(host), port, autoClose) as SSLSocket,
            PoipikuNetworkPolicy.isManaged(host),
            PoipikuNetworkPolicy.sniFor(host),
        )

    override fun createSocket(host: String, port: Int): Socket =
        SniStrippingSocket(
            delegate.createSocket(PoipikuNetworkPolicy.sniFor(host), port) as SSLSocket,
            PoipikuNetworkPolicy.isManaged(host),
            PoipikuNetworkPolicy.sniFor(host),
        )

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        SniStrippingSocket(
            delegate.createSocket(PoipikuNetworkPolicy.sniFor(host), port, localHost, localPort) as SSLSocket,
            PoipikuNetworkPolicy.isManaged(host),
            PoipikuNetworkPolicy.sniFor(host),
        )

    override fun createSocket(host: InetAddress, port: Int): Socket =
        SniStrippingSocket(delegate.createSocket(host, port) as SSLSocket, false, null)

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        SniStrippingSocket(delegate.createSocket(address, port, localAddress, localPort) as SSLSocket, false, null)

    private class SniStrippingSocket(
        private val delegate: SSLSocket,
        private val managed: Boolean,
        private val sniValue: String?,
    ) : SSLSocket() {

        override fun startHandshake() {
            delegate.startHandshake()
        }

        override fun setSSLParameters(params: SSLParameters) {
            // 只对有规则的域名改写 SNI：null 表示清空（握手不带 SNI），
            // 否则替换为伪装值；无规则域名原样转发（ALPN 等其余参数均保留）。
            if (managed) {
                params.serverNames = if (sniValue == null) null else listOf(SNIHostName(sniValue))
            }
            delegate.setSSLParameters(params)
        }

        override fun getSession(): SSLSession = delegate.session
        override fun getHandshakeSession(): SSLSession = delegate.handshakeSession
        override fun setUseClientMode(mode: Boolean) { delegate.useClientMode = mode }
        override fun getUseClientMode(): Boolean = delegate.useClientMode
        override fun setNeedClientAuth(need: Boolean) { delegate.needClientAuth = need }
        override fun setWantClientAuth(want: Boolean) { delegate.wantClientAuth = want }
        override fun getNeedClientAuth(): Boolean = delegate.needClientAuth
        override fun getWantClientAuth(): Boolean = delegate.wantClientAuth
        override fun setEnableSessionCreation(flag: Boolean) { delegate.enableSessionCreation = flag }
        override fun getEnableSessionCreation(): Boolean = delegate.enableSessionCreation
        override fun addHandshakeCompletedListener(listener: HandshakeCompletedListener) =
            delegate.addHandshakeCompletedListener(listener)

        override fun removeHandshakeCompletedListener(listener: HandshakeCompletedListener) =
            delegate.removeHandshakeCompletedListener(listener)

        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
        override fun getEnabledCipherSuites(): Array<String> = delegate.enabledCipherSuites
        override fun setEnabledCipherSuites(suites: Array<String>) = delegate.setEnabledCipherSuites(suites)
        override fun getSupportedProtocols(): Array<String> = delegate.supportedProtocols
        override fun getEnabledProtocols(): Array<String> = delegate.enabledProtocols
        override fun setEnabledProtocols(protocols: Array<String>) = delegate.setEnabledProtocols(protocols)
        override fun getSSLParameters(): SSLParameters = delegate.sslParameters

        override fun getInetAddress(): InetAddress = delegate.inetAddress
        override fun getLocalAddress(): InetAddress = delegate.localAddress
        override fun getPort(): Int = delegate.port
        override fun getLocalPort(): Int = delegate.localPort
        override fun getRemoteSocketAddress(): SocketAddress? = delegate.remoteSocketAddress
        override fun getLocalSocketAddress(): SocketAddress? = delegate.localSocketAddress
        override fun getInputStream(): InputStream = delegate.getInputStream()
        override fun getOutputStream(): OutputStream = delegate.getOutputStream()
        override fun setTcpNoDelay(on: Boolean) { delegate.tcpNoDelay = on }
        override fun getTcpNoDelay(): Boolean = delegate.tcpNoDelay
        override fun setSoLinger(on: Boolean, linger: Int) = delegate.setSoLinger(on, linger)
        override fun getSoLinger(): Int = delegate.soLinger
        override fun sendUrgentData(data: Int) = delegate.sendUrgentData(data)
        override fun setOOBInline(on: Boolean) { delegate.oobInline = on }
        override fun getOOBInline(): Boolean = delegate.oobInline
        override fun setSoTimeout(timeout: Int) { delegate.soTimeout = timeout }
        override fun getSoTimeout(): Int = delegate.soTimeout
        override fun setSendBufferSize(size: Int) { delegate.sendBufferSize = size }
        override fun getSendBufferSize(): Int = delegate.sendBufferSize
        override fun setReceiveBufferSize(size: Int) { delegate.receiveBufferSize = size }
        override fun getReceiveBufferSize(): Int = delegate.receiveBufferSize
        override fun setKeepAlive(on: Boolean) { delegate.keepAlive = on }
        override fun getKeepAlive(): Boolean = delegate.keepAlive
        override fun setTrafficClass(tc: Int) { delegate.trafficClass = tc }
        override fun getTrafficClass(): Int = delegate.trafficClass
        override fun setReuseAddress(on: Boolean) { delegate.reuseAddress = on }
        override fun getReuseAddress(): Boolean = delegate.reuseAddress
        override fun shutdownInput() = delegate.shutdownInput()
        override fun shutdownOutput() = delegate.shutdownOutput()
        override fun toString(): String = delegate.toString()
        override fun isConnected(): Boolean = delegate.isConnected
        override fun isBound(): Boolean = delegate.isBound
        override fun isClosed(): Boolean = delegate.isClosed
        override fun isInputShutdown(): Boolean = delegate.isInputShutdown
        override fun isOutputShutdown(): Boolean = delegate.isOutputShutdown
        override fun getChannel(): SocketChannel? = delegate.channel

        @Throws(IOException::class)
        override fun bind(bindpoint: SocketAddress?) = delegate.bind(bindpoint)

        @Throws(IOException::class)
        override fun connect(endpoint: SocketAddress, timeout: Int) = delegate.connect(endpoint, timeout)

        @Throws(IOException::class)
        override fun connect(endpoint: SocketAddress) = delegate.connect(endpoint)

        @Throws(IOException::class)
        override fun close() = delegate.close()

        @Throws(SocketException::class)
        override fun setPerformancePreferences(connectionTime: Int, latency: Int, bandwidth: Int) =
            delegate.setPerformancePreferences(connectionTime, latency, bandwidth)
    }
}