package com.piku.client.data.remote

import java.net.CookieStore
import java.net.HttpCookie
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar(
    private val cookieStore: CookieStore,
    private val sessionMonitor: SessionMonitor,
) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        android.util.Log.d(
            TAG,
            "saveFromResponse: ${url.encodedPath} -> ${cookies.map { "${it.name}=${it.value.take(16)}" }}",
        )
        val uri = url.toUri()
        cookies.forEach { cookie ->
            if (cookie.name == SESSION_COOKIE && cookie.value.isBlank()) {
                android.util.Log.d(TAG, "session cookie cleared by server")
                sessionMonitor.notifySessionCleared()
            }
            val httpCookie = HttpCookie(cookie.name, cookie.value)
            httpCookie.domain = cookie.domain
            httpCookie.path = cookie.path
            httpCookie.isHttpOnly = cookie.httpOnly
            httpCookie.secure = cookie.secure
            httpCookie.maxAge = if (cookie.persistent) {
                (cookie.expiresAt - System.currentTimeMillis()) / 1000
            } else {
                -1
            }
            cookieStore.add(uri, httpCookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val uri = url.toUri()
        val cookies = cookieStore.get(uri)
            .filter { it.value.isNotBlank() && !it.hasExpired() }
            .map { httpCookie ->
                Cookie.Builder()
                    .name(httpCookie.name)
                    .value(httpCookie.value)
                    .domain(httpCookie.domain)
                    .path(httpCookie.path)
                    .also { builder ->
                        if (httpCookie.isHttpOnly) builder.httpOnly()
                        if (httpCookie.secure) builder.secure()
                    }
                    .build()
            }
        android.util.Log.d(
            TAG,
            "loadForRequest: ${url.encodedPath} -> ${cookies.map { "${it.name}=${it.value.take(16)}" }}",
        )
        return cookies
    }

    private companion object {
        const val TAG = "PikuDiag"
        const val SESSION_COOKIE = "POIPIKU_LK"
    }
}