package com.piku.client.data.repository

import com.piku.client.data.local.CredentialCipher
import com.piku.client.data.local.CredentialStorage
import com.piku.client.data.local.CredentialStore
import com.piku.client.data.local.InMemorySharedPreferences
import com.piku.client.data.local.PopularTagCacheRepository
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.FileCookieStore
import com.piku.client.data.remote.PersistentCookieJar
import com.piku.client.data.remote.PoipikuApi
import com.piku.client.data.remote.SessionMonitor
import com.piku.client.domain.model.AppError
import java.lang.reflect.Proxy
import java.net.CookieStore
import java.net.HttpCookie
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import retrofit2.Response

/**
 * FeedRepository 的会话失效兜底。
 *
 * 实测：会话过期时 `SearchUserByKeywordPcV.jsp` 直接回 **HTTP 404**（不是登录页），
 * 而 apiCall 把 404 映射成 AppError.NotFound——所以这里必须判 NotFound。
 */
class FeedRepositorySessionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val collectScope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun userSearch404NotifiesSessionCleared() = runBlocking {
        val monitor = SessionMonitor()
        val fires = mutableListOf<Unit>()
        val job = collectScope.launch { monitor.sessionCleared.collect { fires += Unit } }
        val repo = feedRepository(apiThrowing404 = true, monitor = monitor)

        val result = repo.getUserSearch("test", 0)

        assertTrue(result.isFailure)
        assertEquals("过期 token 下该接口回 404，必须当成会话失效", 1, fires.size)
        job.cancel()
    }

    @Test
    fun otherErrorsDoNotNotifySessionCleared() = runBlocking {
        val monitor = SessionMonitor()
        val fires = mutableListOf<Unit>()
        val job = collectScope.launch { monitor.sessionCleared.collect { fires += Unit } }
        // 未登录时的 404 属正常现象，不该触发重登
        val repo = feedRepository(apiThrowing404 = true, monitor = monitor, loggedIn = false)

        repo.getUserSearch("test", 0)

        assertEquals(0, fires.size)
        job.cancel()
    }

    // ---- 工具 ----

    private fun feedRepository(
        apiThrowing404: Boolean,
        monitor: SessionMonitor,
        loggedIn: Boolean = true,
    ): FeedRepository {
        val credentials = credentialStore(loggedInUid = loggedIn)
        val store = cookieStore(withSession = loggedIn)
        val runtime = SessionRuntime(Dispatchers.Unconfined) { 0L }
        val auth = AuthRepository(
            authApi = AuthApiProxy().api,
            cookieJar = PersistentCookieJar(store, monitor),
            cookieStore = store,
            sessionMonitor = monitor,
            credentialStore = credentials,
            blockListRepository = BlockListRepository(),
            runtime = runtime,
        )
        return FeedRepository(
            api = PoipikuApiProxy(apiThrowing404).api,
            settingsRepository = SettingsRepository(InMemorySharedPreferences()),
            authRepository = auth,
            profileRepository = ProfileRepository(
                authApi = AuthApiProxy().api,
                credentialStore = credentials,
                authRepository = auth,
                runtime = runtime,
            ),
            sessionMonitor = monitor,
            popularTagCacheRepository = PopularTagCacheRepository(InMemorySharedPreferences()),
        )
    }

    private fun cookieStore(withSession: Boolean): CookieStore {
        val store = FileCookieStore(tmp.newFile("cookies.properties"))
        if (withSession) {
            store.add(
                URI("https://poipiku.com/"),
                HttpCookie("POIPIKU_LK", "token").apply {
                    domain = "poipiku.com"
                    path = "/"
                },
            )
        }
        return store
    }

    private fun credentialStore(loggedInUid: Boolean): CredentialStore =
        CredentialStore(
            FakeStorage().apply {
                if (loggedInUid) {
                    put("uid", UID.toString())
                    put("email_enc", "ENC:$EMAIL")
                    put("password_enc", "ENC:$PASSWORD")
                }
            },
            FakeCipher(),
        )

    /** 除 getUserSearch 外一律抛错：本测试只走那一条路径 */
    private class PoipikuApiProxy(private val throws404: Boolean) {
        val api: PoipikuApi = Proxy.newProxyInstance(
            PoipikuApi::class.java.classLoader,
            arrayOf(PoipikuApi::class.java),
        ) { _, method, _ ->
            if (method.name == "getUserSearch" && throws404) {
                throw HttpException(Response.error<Any>(404, "".toResponseBody("text/html".toMediaTypeOrNull())))
            }
            throw UnsupportedOperationException(method.name)
        } as PoipikuApi
    }

    /** AuthRepository 构造要它，本测试不会真的登录 */
    private class AuthApiProxy {
        val api: com.piku.client.data.remote.AuthApi = Proxy.newProxyInstance(
            com.piku.client.data.remote.AuthApi::class.java.classLoader,
            arrayOf(com.piku.client.data.remote.AuthApi::class.java),
        ) { _, method, _ -> throw UnsupportedOperationException(method.name) }
            as com.piku.client.data.remote.AuthApi
    }

    private class FakeCipher : CredentialCipher {
        override fun encrypt(plain: String): String = "ENC:$plain"
        override fun decrypt(cipherText: String): String = cipherText.removePrefix("ENC:")
    }

    private class FakeStorage : CredentialStorage {
        private val map = HashMap<String, String>()
        override fun get(key: String): String? = map[key]
        override fun put(key: String, value: String) {
            map[key] = value
        }

        override fun remove(key: String) {
            map.remove(key)
        }
    }

    private companion object {
        const val EMAIL = "user@example.com"
        const val PASSWORD = "secret123"
        const val UID = 14189264L
    }
}
