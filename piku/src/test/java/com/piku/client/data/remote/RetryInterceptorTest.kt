package com.piku.client.data.remote

import com.piku.client.data.local.InMemorySharedPreferences
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ProxySelector
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import kotlin.reflect.KClass

class RetryInterceptorTest {

    private val dns = DoHDns(InMemorySharedPreferences())
    private val interceptor = RetryInterceptor(dns)

    private val request = Request.Builder()
        .url("https://poipiku.com/")
        .get()
        .build()

    private class FakeCall(
        private val request: Request,
        var canceled: Boolean = false,
    ) : Call {
        override fun request(): Request = request
        override fun execute(): Response = throw IOException()
        override fun enqueue(responseCallback: Callback) = Unit
        override fun cancel() { canceled = true }
        override fun isCanceled(): Boolean = canceled
        override fun isExecuted(): Boolean = false
        override fun timeout(): Timeout = Timeout()
        override fun clone(): Call = FakeCall(request, canceled)
        override fun addEventListener(eventListener: EventListener) = Unit
        override fun <T : Any> tag(type: KClass<T>): T? = null
        override fun <T> tag(type: Class<out T>): T? = null
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T =
            computeIfAbsent()
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T =
            computeIfAbsent()
    }

    private class FakeChain(
        private val request: Request,
        private val call: Call,
        private val results: Array<out Any>,
        var proceedCount: Int = 0,
        val hosts: MutableList<String> = mutableListOf(),
    ) : Interceptor.Chain {
        override fun request(): Request = request
        override fun proceed(request: Request): Response {
            proceedCount++
            hosts += request.url.host
            return when (val r = results[proceedCount - 1]) {
                is Response -> r
                else -> throw (r as IOException)
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

    private fun response(code: Int): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .build()

    @Test
    fun retriesOnIOExceptionUntilSuccess() {
        val chain = FakeChain(
            request, FakeCall(request),
            arrayOf(IOException("boom"), response(200)),
        )

        val result = interceptor.intercept(chain)

        assertEquals(200, result.code)
        assertEquals(2, chain.proceedCount)
    }

    @Test
    fun retriesOnSocketTimeoutOnlyOnce() {
        val chain = FakeChain(
            request, FakeCall(request),
            arrayOf(SocketTimeoutException("timeout"), response(200)),
        )

        val result = interceptor.intercept(chain)

        assertEquals(200, result.code)
        assertEquals(2, chain.proceedCount)
    }

    @Test
    fun socketTimeoutDoesNotRetryTwice() {
        val chain = FakeChain(
            request, FakeCall(request),
            arrayOf(
                SocketTimeoutException("t1"),
                SocketTimeoutException("t2"),
                SocketTimeoutException("t3"),
            ),
        )

        val thrown = runCatching { interceptor.intercept(chain) }.exceptionOrNull()

        assertTrue(thrown is SocketTimeoutException)
        // 初始 + 1 次重试 = 2 次，不会第三次
        assertEquals(2, chain.proceedCount)
    }

    @Test
    fun plainIOExceptionFollowedByTimeoutStillGetsTimeoutRetry() {
        val chain = FakeChain(
            request, FakeCall(request),
            arrayOf(IOException("net"), SocketTimeoutException("timeout"), response(200)),
        )

        val result = interceptor.intercept(chain)

        assertEquals(200, result.code)
        assertEquals(3, chain.proceedCount)
    }

    @Test
    fun throwsAfterMaxAttempts() {
        val chain = FakeChain(
            request, FakeCall(request),
            arrayOf(IOException("1"), IOException("2"), IOException("3")),
        )

        val thrown = runCatching { interceptor.intercept(chain) }.exceptionOrNull()

        assertTrue(thrown is IOException)
        assertEquals(3, chain.proceedCount)
    }

    @Test
    fun doesNotRetryWhenCallCanceled() {
        val chain = FakeChain(
            request, FakeCall(request, canceled = true),
            arrayOf(IOException("cancelled"), response(200)),
        )

        val thrown = runCatching { interceptor.intercept(chain) }.exceptionOrNull()

        assertTrue(thrown is IOException)
        assertEquals(0, chain.proceedCount)
    }

    @Test
    fun retriesHttp429() {
        val chain = FakeChain(
            request, FakeCall(request),
            arrayOf(response(429), response(200)),
        )

        val result = interceptor.intercept(chain)

        assertEquals(200, result.code)
        assertEquals(2, chain.proceedCount)
    }

    @Test
    fun retriesHttp5xx() {
        val chain = FakeChain(
            request, FakeCall(request),
            arrayOf(response(503), response(200)),
        )

        val result = interceptor.intercept(chain)

        assertEquals(200, result.code)
        assertEquals(2, chain.proceedCount)
    }

    @Test
    fun returnsLastResponseAfterHttpRetriesExhausted() {
        val chain = FakeChain(
            request, FakeCall(request),
            arrayOf(response(503), response(502), response(500)),
        )

        val result = interceptor.intercept(chain)

        assertEquals(500, result.code)
        assertEquals(3, chain.proceedCount)
    }

    @Test
    fun doesNotRetryOtherHttpCodes() {
        val chain = FakeChain(
            request, FakeCall(request),
            arrayOf(response(404)),
        )

        val result = interceptor.intercept(chain)

        assertEquals(404, result.code)
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun doesNotRetryPostRequests() {
        val post = Request.Builder()
            .url("https://poipiku.com/login")
            .post(okhttp3.RequestBody.create(null, "{}"))
            .build()
        val chain = FakeChain(
            post, FakeCall(post),
            arrayOf(IOException("no retry")),
        )

        val thrown = runCatching { interceptor.intercept(chain) }.exceptionOrNull()

        assertTrue(thrown is IOException)
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun reusesSameRequestBetweenRetries() {
        val chain = FakeChain(
            request, FakeCall(request),
            arrayOf(IOException("boom"), response(200)),
        )

        interceptor.intercept(chain)

        assertEquals(listOf("poipiku.com", "poipiku.com"), chain.hosts)
    }
}