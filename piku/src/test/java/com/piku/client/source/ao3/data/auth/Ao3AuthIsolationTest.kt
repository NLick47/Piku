package com.piku.client.source.ao3.data.auth

import com.piku.client.core.model.AuthStatus
import com.piku.client.core.model.WorkSource
import com.piku.client.core.storage.prefs.CredentialStorage
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response
import java.net.HttpCookie
import java.net.URI

class Ao3AuthIsolationTest {

    @Test
    fun logoutClearsOnlyAo3Cookies() {
        val cookies = MemoryCookieStore().apply {
            put(cookie("user_credentials", "1", "archiveofourown.org"))
            put(cookie("_otwarchive_session", "abc", ".archiveofourown.org"))
            put(cookie("PHPSESSID", "poipiku", "poipiku.com"))
        }
        val prefs = MemoryStorage().apply { put("username", "uu6kk") }
        val auth = auth(cookies, prefs)

        assertEquals(WorkSource.AO3, auth.source)
        assertEquals(AuthStatus.LOGGED_IN, auth.status.value)
        assertEquals("uu6kk", auth.account.value?.displayName)

        auth.logout()

        assertEquals(AuthStatus.LOGGED_OUT, auth.status.value)
        assertNull(auth.account.value)
        assertNull(prefs.get("username"))
        assertEquals(listOf("PHPSESSID"), cookies.getCookies().map { it.name })
    }

    /** 冷启动的登录态来自落盘的 cookie + 用户名：两者都在才算登录，重开不用再输密码 */
    @Test
    fun coldStartRestoresSession() {
        val cookies = MemoryCookieStore().apply {
            put(cookie("user_credentials", "1", "archiveofourown.org"))
        }
        val prefs = MemoryStorage().apply { put("username", "uu6kk") }

        val auth = auth(cookies, prefs)

        assertEquals(AuthStatus.LOGGED_IN, auth.status.value)
        assertEquals("uu6kk", auth.account.value?.account)
    }

    /** cookie 在而用户名没了（本地数据被清一半）不能留成假登录 */
    @Test
    fun cookieWithoutUsernameIsLoggedOut() {
        val cookies = MemoryCookieStore().apply {
            put(cookie("user_credentials", "1", "archiveofourown.org"))
        }

        val auth = auth(cookies, MemoryStorage())

        assertEquals(AuthStatus.LOGGED_OUT, auth.status.value)
        assertEquals(emptyList<HttpCookie>(), cookies.getCookies())
    }

    private fun auth(cookies: MemoryCookieStore, prefs: MemoryStorage) = Ao3Auth(
        api = NoopApi,
        store = Ao3AuthStore(prefs),
        cookieJar = StoreCookieJar(cookies),
        cookieStore = cookies,
    )

    private fun cookie(name: String, value: String, domain: String) = HttpCookie(name, value).apply {
        this.domain = domain
        path = "/"
    }

    private object NoopApi : Ao3AuthApi {
        override suspend fun loginPage(): Response<ResponseBody> = error("unused")

        override suspend fun login(
            token: String,
            login: String,
            password: String,
            commit: String,
            rememberMe: String,
        ): Response<ResponseBody> = error("unused")
    }

    private class MemoryStorage : CredentialStorage {
        private val values = mutableMapOf<String, String>()

        override fun get(key: String): String? = values[key]

        override fun put(key: String, value: String) {
            values[key] = value
        }

        override fun remove(key: String) {
            values.remove(key)
        }
    }

    private class MemoryCookieStore : java.net.CookieStore {
        private val items = mutableListOf<HttpCookie>()

        fun put(cookie: HttpCookie) {
            items += cookie
        }

        override fun add(uri: URI?, cookie: HttpCookie) {
            items += cookie
        }

        override fun get(uri: URI?): List<HttpCookie> = items.toList()

        override fun getCookies(): List<HttpCookie> = items.toList()

        override fun getURIs(): List<URI> = emptyList()

        override fun remove(uri: URI?, cookie: HttpCookie): Boolean = items.remove(cookie)

        override fun removeAll(): Boolean {
            val had = items.isNotEmpty()
            items.clear()
            return had
        }
    }

    private class StoreCookieJar(private val store: java.net.CookieStore) : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit

        override fun loadForRequest(url: HttpUrl): List<Cookie> = store.getCookies()
            .filter { url.host == it.domain?.removePrefix(".") }
            .map {
                Cookie.Builder()
                    .name(it.name)
                    .value(it.value)
                    .domain(url.host)
                    .path(it.path ?: "/")
                    .build()
            }
    }
}
