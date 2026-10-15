package com.piku.client.core.network.dns
import com.piku.client.core.network.diag.NetworkDiagnostics

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
    private val mayBeRelayed: (String) -> Boolean,
) : EventListener() {

    private var host: String? = null
    private var address: InetAddress? = null

    override fun connectionAcquired(call: Call, connection: Connection) {
        val connectedHost = connection.route().address.url.host
        val acquired = connection.route().socketAddress.address
        host = connectedHost
        address = acquired
        dns.reportSuccess(connectedHost, acquired)
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
        val target = call.request().url.host
        val failed = inetSocketAddress.address
        if (mayBeRelayed(target)) {
            diagnostics.warn(
                "connect failed host=$target addr=${failed.hostAddress}（可能走中继，不计入地址簿）" +
                    " ${ioe.javaClass.simpleName}: ${ioe.message}",
            )
            return
        }
        dns.reportFailure(
            target,
            failed,
            if (ioe is SSLException) DoHDns.FailureType.TLS else DoHDns.FailureType.CONNECT,
        )
        diagnostics.warn(
            "connect failed host=$target addr=${failed.hostAddress} " +
                "${ioe.javaClass.simpleName}: ${ioe.message}",
        )
    }

    override fun responseFailed(call: Call, ioe: IOException) {
        if (call.isCanceled()) return
        val target = host ?: call.request().url.host
        address?.let { dns.reportFailure(target, it, DoHDns.FailureType.STREAM) }
        diagnostics.warn("response failed host=$target ${ioe.javaClass.simpleName}: ${ioe.message}")
    }

    override fun requestFailed(call: Call, ioe: IOException) {
        if (call.isCanceled()) return
        val target = host ?: call.request().url.host
        address?.let { dns.reportFailure(target, it, DoHDns.FailureType.STREAM) }
        diagnostics.warn("request failed host=$target ${ioe.javaClass.simpleName}: ${ioe.message}")
    }
}
