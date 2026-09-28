package com.piku.client.data.remote

import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Protocol
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import javax.net.ssl.SSLException

internal class DoHEventListener(
    private val dns: DoHDns,
    private val diagnostics: NetworkDiagnostics,
) : EventListener() {

    private var address: InetAddress? = null

    override fun connectionAcquired(call: Call, connection: Connection) {
        val acquired = connection.route().socketAddress.address
        address = acquired
        dns.reportSuccess(call.request().url.host, acquired)
        // 记下协商结果：SniStrippingSocket 是自建 SSLSocket 包装，
        // ALPN 读不到时会静默退回 HTTP/1.1，这里留个可查的痕迹
        diagnostics.recordConnectionProtocol(connection.protocol().toString())
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException,
    ) {
        if (call.isCanceled()) return
        val host = call.request().url.host
        dns.reportFailure(
            host,
            inetSocketAddress.address,
            if (ioe is SSLException) DoHDns.FailureType.TLS else DoHDns.FailureType.CONNECT,
        )
        diagnostics.warn(
            "connect failed host=$host addr=${inetSocketAddress.address.hostAddress} " +
                "${ioe.javaClass.simpleName}: ${ioe.message}",
        )
    }

    override fun responseFailed(call: Call, ioe: IOException) {
        if (call.isCanceled()) return
        val host = call.request().url.host
        address?.let { dns.reportFailure(host, it, DoHDns.FailureType.STREAM) }
        diagnostics.warn("response failed host=$host ${ioe.javaClass.simpleName}: ${ioe.message}")
    }

    override fun requestFailed(call: Call, ioe: IOException) {
        if (call.isCanceled()) return
        val host = call.request().url.host
        address?.let { dns.reportFailure(host, it, DoHDns.FailureType.STREAM) }
        diagnostics.warn("request failed host=$host ${ioe.javaClass.simpleName}: ${ioe.message}")
    }
}
