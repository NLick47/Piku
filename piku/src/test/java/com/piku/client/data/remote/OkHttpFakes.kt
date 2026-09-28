package com.piku.client.data.remote

import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Connection
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import java.io.IOException
import java.net.ProxySelector
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import kotlin.reflect.KClass

/** 拦截器单测共用：省掉每个测试类重写一遍 OkHttp 的全部成员 */
internal class FakeCall(
    private val request: Request,
    var canceled: Boolean = false,
    private val executeResult: (() -> Response)? = null,
) : Call {

    override fun request(): Request = request
    override fun execute(): Response = executeResult?.invoke() ?: throw IOException()
    override fun enqueue(responseCallback: Callback) = Unit
    override fun cancel() { canceled = true }
    override fun isCanceled(): Boolean = canceled
    override fun isExecuted(): Boolean = false
    override fun timeout(): Timeout = Timeout()
    override fun clone(): Call = FakeCall(request, canceled)
    override fun addEventListener(eventListener: EventListener) = Unit
    override fun <T : Any> tag(type: KClass<T>): T? = null
    override fun <T> tag(type: Class<out T>): T? = null
    override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
    override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
}

/** 按顺序应答：Response 直接返回，IOException 抛给调用方；多出来的 proceed 视为编排错误 */
internal class FakeChain(
    private val request: Request,
    private val call: FakeCall,
    private val results: List<Any>,
) : Interceptor.Chain {

    val proceeded = mutableListOf<Request>()

    val hosts: List<String> get() = proceeded.map { it.url.host }

    override fun request(): Request = request

    override fun proceed(request: Request): Response {
        val result = results.getOrNull(proceeded.size)
        proceeded += request
        return when (result) {
            null -> error("unexpected proceed: ${request.url}")
            is Response -> result
            else -> throw result as IOException
        }
    }

    override fun call(): Call = call
    override fun connection(): Connection? = null
    override fun connectTimeoutMillis(): Int = 5_000
    override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    override fun readTimeoutMillis(): Int = 30_000
    override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    override fun writeTimeoutMillis(): Int = 30_000
    override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    override fun withDns(dns: Dns): Interceptor.Chain = this
    override fun withSocketFactory(socketFactory: javax.net.SocketFactory): Interceptor.Chain = this
    override fun withRetryOnConnectionFailure(retryOnConnectionFailure: Boolean): Interceptor.Chain = this
    override fun withAuthenticator(authenticator: Authenticator): Interceptor.Chain = this
    override fun withCookieJar(cookieJar: okhttp3.CookieJar): Interceptor.Chain = this
    override fun withCache(cache: okhttp3.Cache?): Interceptor.Chain = this
    override fun withProxy(proxy: java.net.Proxy?): Interceptor.Chain = this
    override fun withProxySelector(proxySelector: ProxySelector): Interceptor.Chain = this
    override fun withProxyAuthenticator(proxyAuthenticator: Authenticator): Interceptor.Chain = this
    override fun withSslSocketFactory(
        sslSocketFactory: javax.net.ssl.SSLSocketFactory?,
        x509TrustManager: javax.net.ssl.X509TrustManager?,
    ): Interceptor.Chain = this
    override fun withHostnameVerifier(hostnameVerifier: HostnameVerifier): Interceptor.Chain = this
    override fun withCertificatePinner(certificatePinner: okhttp3.CertificatePinner): Interceptor.Chain = this
    override fun withConnectionPool(connectionPool: okhttp3.ConnectionPool): Interceptor.Chain = this
    override val followSslRedirects: Boolean get() = true
    override val followRedirects: Boolean get() = true
    override val dns: Dns get() = Dns.SYSTEM
    override val socketFactory: javax.net.SocketFactory get() = javax.net.SocketFactory.getDefault()
    override val retryOnConnectionFailure: Boolean get() = true
    override val authenticator: Authenticator get() = Authenticator.NONE
    override val cookieJar: okhttp3.CookieJar get() = okhttp3.CookieJar.NO_COOKIES
    override val cache: okhttp3.Cache? get() = null
    override val proxy: java.net.Proxy? get() = null
    override val proxySelector: ProxySelector get() = ProxySelector.getDefault()
    override val proxyAuthenticator: Authenticator get() = Authenticator.NONE
    override val sslSocketFactoryOrNull: javax.net.ssl.SSLSocketFactory? get() = null
    override val x509TrustManagerOrNull: javax.net.ssl.X509TrustManager? get() = null
    override val hostnameVerifier: HostnameVerifier get() = HostnameVerifier { _, _ -> true }
    override val certificatePinner: okhttp3.CertificatePinner get() = okhttp3.CertificatePinner.DEFAULT
    override val connectionPool: okhttp3.ConnectionPool get() = okhttp3.ConnectionPool()
    override val eventListener: EventListener get() = EventListener.NONE
}

internal fun okResponse(request: Request, code: Int = 200): Response =
    Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .build()
