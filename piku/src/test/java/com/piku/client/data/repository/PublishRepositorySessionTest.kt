package com.piku.client.data.repository

import com.piku.client.data.local.CredentialCipher
import com.piku.client.data.local.CredentialStorage
import com.piku.client.data.local.CredentialStore
import com.piku.client.data.remote.AuthApi
import com.piku.client.data.remote.FileCookieStore
import com.piku.client.data.remote.PersistentCookieJar
import com.piku.client.data.remote.SessionMonitor
import com.piku.client.data.remote.UploadApi
import com.piku.client.domain.model.PublishDraft
import com.piku.client.domain.model.UploadKind
import java.lang.reflect.Proxy
import java.net.CookieStore
import java.net.HttpCookie
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response

class PublishRepositorySessionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val collectScope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun loginPageResponseFailsPublishAndNotifiesSessionCleared() = runBlocking {
        val monitor = SessionMonitor()
        val fires = mutableListOf<Unit>()
        val job = collectScope.launch { monitor.sessionCleared.collect { fires += Unit } }
        val repo = publishRepository(monitor, respondWith = LOGIN_PAGE)

        val result = repo.createNovel(draft())

        assertTrue(
            "登录页响应必须报会话失效而不是别的错误，实际 ${result.exceptionOrNull()}",
            result.exceptionOrNull() is PublishFailure.SessionExpired,
        )
        assertEquals("同时要通知会话失效，页面才会去重登", 1, fires.size)
        job.cancel()
    }

    @Test
    fun successfulPublishDoesNotNotifySessionCleared() = runBlocking {
        val monitor = SessionMonitor()
        val fires = mutableListOf<Unit>()
        val job = collectScope.launch { monitor.sessionCleared.collect { fires += Unit } }
        val repo = publishRepository(monitor, respondWith = """{"content_id": 12345}""")

        val result = repo.createNovel(draft())

        assertEquals(12345L, result.getOrNull())
        assertEquals(0, fires.size)
        job.cancel()
    }

    /** 未登录时不该发请求，直接报 NotLoggedIn */
    @Test
    fun notLoggedInFailsWithoutRequest() = runBlocking {
        val calls = intArrayOf(0)
        val repo = publishRepository(SessionMonitor(), respondWith = "{}", loggedIn = false, calls = calls)

        val result = repo.createNovel(draft())

        assertTrue(result.exceptionOrNull() is PublishFailure.NotLoggedIn)
        assertEquals("未登录不该打发布接口", 0, calls[0])
    }

    // ---- 工具 ----

    private fun publishRepository(
        monitor: SessionMonitor,
        respondWith: String,
        loggedIn: Boolean = true,
        calls: IntArray = intArrayOf(0),
    ): PublishRepository {
        val store = cookieStore(loggedIn)
        return PublishRepository(
            api = FakeUploadApi(respondWith, calls).api,
            poipikuApi = Proxy.newProxyInstance(
                com.piku.client.data.remote.PoipikuApi::class.java.classLoader,
                arrayOf(com.piku.client.data.remote.PoipikuApi::class.java),
            ) { _, method, _ -> throw UnsupportedOperationException(method.name) }
                as com.piku.client.data.remote.PoipikuApi,
            json = Json { ignoreUnknownKeys = true },
            authRepository = AuthRepository(
                authApi = Proxy.newProxyInstance(
                    AuthApi::class.java.classLoader,
                    arrayOf(AuthApi::class.java),
                ) { _, method, _ -> throw UnsupportedOperationException(method.name) } as AuthApi,
                cookieJar = PersistentCookieJar(store, monitor),
                cookieStore = store,
                sessionMonitor = monitor,
                credentialStore = credentialStore(loggedIn),
                blockListRepository = BlockListRepository(),
                runtime = SessionRuntime(Dispatchers.Unconfined) { 0L },
            ),
            sessionMonitor = monitor,
        )
    }

    private fun draft() = PublishDraft(kind = UploadKind.NOVEL, tags = "test", publish = true)

    private fun cookieStore(loggedIn: Boolean): CookieStore {
        val store = FileCookieStore(tmp.newFile("cookies.properties"))
        store.add(
            URI("https://poipiku.com/"),
            HttpCookie("POIPIKU_LK", if (loggedIn) "token" else "").apply {
                domain = "poipiku.com"
                path = "/"
            },
        )
        return store
    }

    private fun credentialStore(loggedIn: Boolean) = CredentialStore(
        FakeStorage().apply {
            if (loggedIn) {
                put("uid", UID.toString())
                put("email_enc", "ENC:$EMAIL")
                put("password_enc", "ENC:$PASSWORD")
            }
        },
        FakeCipher(),
    )

    private class FakeUploadApi(respondWith: String, private val calls: IntArray) {
        val api: UploadApi = Proxy.newProxyInstance(
            UploadApi::class.java.classLoader,
            arrayOf(UploadApi::class.java),
        ) { _, method, _ ->
            calls[0]++
            Response.success<ResponseBody>(respondWith.toResponseBody("text/html".toMediaTypeOrNull()))
        } as UploadApi
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

        /** 会话失效时服务端可能回的就是这一页 */
        val LOGIN_PAGE = """
            <html><head><title>ログイン</title></head>
            <body><form action="/f/LoginUserF.jsp" method="post"></form></body></html>
        """.trimIndent()
    }
}
