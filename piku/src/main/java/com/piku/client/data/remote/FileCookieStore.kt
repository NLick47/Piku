package com.piku.client.data.remote

import java.io.File
import java.net.CookieStore
import java.net.HttpCookie
import java.net.URI
import java.util.Properties

class FileCookieStore(private val file: File) : CookieStore {

    private val cookies = HashMap<String, HttpCookie>()

    init {
        file.parentFile?.mkdirs()
        load()
    }

    @Synchronized
    override fun add(uri: URI?, cookie: HttpCookie) {
        android.util.Log.d(
            TAG,
            "add: ${cookie.name}=${cookie.value.take(16)} domain='${cookie.domain}' path='${cookie.path}' " +
                "secure=${cookie.secure} httpOnly=${cookie.isHttpOnly} maxAge=${cookie.maxAge} uri=${uri?.host}",
        )
        cookies[key(cookie)] = cookie
        save()
    }

    @Synchronized
    override fun get(uri: URI?): List<HttpCookie> {
        val result = cookies.values.filter { !it.hasExpired() && matches(it, uri) }
        android.util.Log.d(
            TAG,
            "get: uri=${uri?.host ?: "null"} -> ${result.map { "${it.name}=${it.value.take(16)}" }}",
        )
        return result
    }

    @Synchronized
    override fun getCookies(): List<HttpCookie> = cookies.values.toList()

    @Synchronized
    override fun getURIs(): List<URI> = emptyList()

    @Synchronized
    override fun remove(uri: URI?, cookie: HttpCookie): Boolean {
        val removed = cookies.remove(key(cookie)) != null
        if (removed) save()
        return removed
    }

    @Synchronized
    override fun removeAll(): Boolean {
        val had = cookies.isNotEmpty()
        cookies.clear()
        if (had) save()
        return had
    }

    private fun matches(cookie: HttpCookie, uri: URI?): Boolean {
        if (uri == null) return true
        val host = uri.host ?: return false
        val domain = cookie.domain?.removePrefix(".") ?: return host == uri.host
        val hostMatches = host == domain || host.endsWith(".$domain")
        return hostMatches && (uri.path ?: "/").startsWith(cookie.path ?: "/")
    }

    private fun key(cookie: HttpCookie): String =
        "${cookie.name}\u0000${cookie.domain}\u0000${cookie.path}"

    private companion object {
        const val TAG = "PikuDiag"
    }

    private fun save() {
        try {
            val props = Properties()
            cookies.forEach { (k, cookie) ->
                props[k] = listOf(
                    cookie.value,
                    cookie.maxAge.toString(),
                    cookie.secure.toString(),
                    cookie.isHttpOnly.toString(),
                ).joinToString("\u0001")
            }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.outputStream().use { props.store(it, null) }
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) error("cookie store replace failed: ${file.name}")
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "cookie save failed: ${file.name}", e)
        }
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            props.forEach { (k, v) ->
                val fields = (v as String).split("\u0001")
                val parts = (k as String).split("\u0000")
                if (fields.size == 4 && parts.size == 3) {
                    val cookie = HttpCookie(parts[0], fields[0])
                    cookie.domain = parts[1]
                    cookie.path = parts[2]
                    cookie.maxAge = fields[1].toLongOrNull() ?: -1L
                    cookie.secure = fields[2].toBoolean()
                    cookie.isHttpOnly = fields[3].toBoolean()
                    cookies[k] = cookie
                }
            }
            android.util.Log.d(TAG, "load: file=${file.absolutePath} -> ${cookies.map { it.value.name }}")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "cookie store corrupt, resetting: ${file.name}", e)
            cookies.clear()
            runCatching { file.delete() }
        }
    }
}
