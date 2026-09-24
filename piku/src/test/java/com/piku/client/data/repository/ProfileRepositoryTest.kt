package com.piku.client.data.repository

import com.piku.client.data.local.CredentialCipher
import com.piku.client.data.local.CredentialStorage
import com.piku.client.data.local.CredentialStore
import com.piku.client.data.remote.AuthApi
import com.piku.client.data.remote.FileCookieStore
import com.piku.client.data.remote.LoginResponse
import com.piku.client.data.remote.PersistentCookieJar
import com.piku.client.data.remote.SessionMonitor
import java.lang.reflect.Proxy
import java.net.CookieStore
import java.net.HttpCookie
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response

/**
 * 资料卡：刷新由 [AuthRepository.sessionVersion] 驱动，一次会话变化只刷一次。
 * 以前这条链路的"谁该刷"散在两处（login 一次 + 首页一次），这里是它的守卫。
 */
class ProfileRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun sessionRestoredAtColdStartRefreshesProfileOnce() {
        val api = FakeAuthApi()
        val (_, profiles) = build(api, cookieStore(sessionValue = TOKEN), loggedInUid = true)

        assertEquals("一次会话变化只该刷一次资料", 1, api.settingCalls)
        assertEquals("サファイア", profiles.userProfile.value?.name)
        assertEquals(
            "https://cdn.poipiku.com/014189264/profile_20260816063418.jpeg_120.jpg",
            profiles.userProfile.value?.avatarUrl,
        )
        assertEquals("https://poipiku.com/$UID/", profiles.userProfile.value?.profileUrl)
    }

    /** 登录也是一次会话变化：刷一次，不多不少 */
    @Test
    fun loginRefreshesProfileOnce() {
        val api = FakeAuthApi()
        val (auth, _) = build(api, cookieStore(sessionValue = BLANK), loggedInUid = false)

        runBlocking { auth.login(EMAIL, PASSWORD) }

        assertEquals("登录只该刷一次资料", 1, api.settingCalls)
    }

    @Test
    fun logoutClearsProfile() {
        val api = FakeAuthApi()
        val (auth, profiles) = build(api, cookieStore(sessionValue = TOKEN), loggedInUid = true)
        assertEquals("サファイア", profiles.userProfile.value?.name)

        auth.logout()

        assertNull("登出后不能留着上一个账号的资料", profiles.userProfile.value)
    }

    /** 刷新失败保留旧缓存：启动时离线不该把抽屉清空 */
    @Test
    fun failedRefreshKeepsCachedProfile() {
        val api = FakeAuthApi()
        val (auth, profiles) = build(api, cookieStore(sessionValue = TOKEN), loggedInUid = true)
        val cached = profiles.userProfile.value

        api.settingThrows = true
        runBlocking { profiles.refresh() }

        assertEquals(cached, profiles.userProfile.value)
    }

    // ---- 工具 ----

    private fun build(
        api: FakeAuthApi,
        store: CookieStore,
        loggedInUid: Boolean,
    ): Pair<AuthRepository, ProfileRepository> {
        val monitor = SessionMonitor()
        val runtime = SessionRuntime(Dispatchers.Unconfined) { 0L }
        val credentials = credentialStore(loggedInUid)
        val auth = AuthRepository(
            authApi = api.api,
            cookieJar = PersistentCookieJar(store, monitor),
            cookieStore = store,
            sessionMonitor = monitor,
            credentialStore = credentials,
            blockListRepository = BlockListRepository(),
            runtime = runtime,
        )
        val profiles = ProfileRepository(
            authApi = api.api,
            credentialStore = credentials,
            authRepository = auth,
            runtime = runtime,
        )
        return auth to profiles
    }

    private fun cookieStore(sessionValue: String): CookieStore {
        val store = FileCookieStore(tmp.newFile("cookies.properties"))
        store.add(
            URI("https://poipiku.com/"),
            HttpCookie("POIPIKU_LK", sessionValue).apply {
                domain = "poipiku.com"
                path = "/"
            },
        )
        return store
    }

    private fun credentialStore(loggedInUid: Boolean) = CredentialStore(
        FakeStorage().apply {
            if (loggedInUid) {
                put("uid", UID.toString())
                put("email_enc", "ENC:$EMAIL")
                put("password_enc", "ENC:$PASSWORD")
            }
        },
        FakeCipher(),
    )

    private class FakeAuthApi {
        var loginCalls = 0
        var settingCalls = 0
        var settingThrows = false

        var loginBehaviour: () -> LoginResponse = { LoginResponse(UID) }

        val api: AuthApi = Proxy.newProxyInstance(
            AuthApi::class.java.classLoader,
            arrayOf(AuthApi::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "login" -> {
                    loginCalls++
                    loginBehaviour()
                }
                "getMyEditSetting" -> {
                    settingCalls++
                    if (settingThrows) throw IllegalStateException("offline")
                    htmlResponse(SETTING_PAGE_HTML)
                }
                "getUserTop" -> htmlResponse(USER_PAGE_HTML)
                else -> throw UnsupportedOperationException(method.name)
            }
        } as AuthApi

        private fun htmlResponse(html: String): Response<ResponseBody> =
            Response.success(html.toResponseBody("text/html".toMediaTypeOrNull()))
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
        const val TOKEN = "66c3af0de94693f0019be93cffab20b3"
        const val BLANK = ""
        const val PASSWORD = "secret123"
        const val UID = 14189264

        /** 设置页：头像 URL 用实测形状（带时间戳、不带尺寸后缀） */
        val SETTING_PAGE_HTML = """
            <section class="PreviewImg" src="https://cdn.poipiku.com/014189264/profile_20260816063418.jpeg">
        """.trimIndent()

        /** og:title 与 h2 故意不一致：用来钉住昵称取的是哪个来源 */
        val USER_PAGE_HTML = """
            <meta property="og:title" content="サファイアのポイピク | イラストとか箱「ポイピク」">
            <h2 class="IllustUserName">别人</h2>
        """.trimIndent()
    }
}
