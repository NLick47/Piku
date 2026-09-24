package com.piku.client.data.repository

import com.piku.client.data.local.CredentialCipher
import com.piku.client.data.local.CredentialStorage
import com.piku.client.data.local.CredentialStore
import com.piku.client.data.local.Credentials
import com.piku.client.data.remote.AuthApi
import com.piku.client.data.remote.FileCookieStore
import com.piku.client.data.remote.LoginResponse
import com.piku.client.data.remote.PersistentCookieJar
import com.piku.client.data.remote.SessionMonitor
import com.piku.client.domain.model.AuthStatus
import com.piku.client.domain.model.LoginError
import java.io.IOException
import java.lang.reflect.Proxy
import java.net.CookieStore
import java.net.HttpCookie
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 会话恢复的仓库层行为。运行环境统一注入 [Dispatchers.Unconfined] + 可控时钟：
 * 不注入的话协程在真实 IO 线程上跑、elapsedRealtime 恒为 0，
 * 断言与协程之间没有同步点，负向断言会"因为还没跑到"而假通过。
 */
class AuthRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /**
     * 订阅 SessionMonitor 这类一次性事件用。Unconfined 下 launch 会内联跑到 collect 挂起，
     * 所以 launch 返回时订阅已生效；不能挂在 runBlocking 里，那样会等子协程而挂死
     */
    private val collectScope = CoroutineScope(Dispatchers.Unconfined)

    // ---- 冷启动 ----

    @Test
    fun coldStartWithoutSessionReLoginsFromSavedCredentials() {
        val api = FakeAuthApi()
        val repo = build(
            api = api,
            store = cookieStore(sessionValue = BLANK),
            credentials = credentialStore().apply { save(EMAIL, PASSWORD) },
        )

        assertTrue(repo.isLoggedIn())
        assertEquals(UID.toLong(), repo.currentUserId())
        assertEquals(1, api.loginCalls)
        assertEquals(PASSWORD, api.lastPassword)
    }

    @Test
    fun coldStartWithFullSessionDoesNotLogin() {
        val api = FakeAuthApi()

        val repo = build(
            api = api,
            store = cookieStore(sessionValue = TOKEN),
            credentials = credentialStore().apply {
                save(EMAIL, PASSWORD)
                saveUid(UID.toLong())
            },
        )

        assertEquals(0, api.loginCalls)
        assertTrue(repo.isLoggedIn())
    }

    @Test
    fun coldStartWithoutCredentialsDoesNotLogin() {
        val api = FakeAuthApi()
        val storage = FakeStorage()
        val repo = build(
            api = api,
            store = cookieStore(sessionValue = BLANK),
            credentials = CredentialStore(storage, FakeCipher()),
        )

        assertTrue("冷启动应该去读一次凭据", storage.awaitRead(KEY_EMAIL))
        assertFalse(repo.isLoggedIn())
        assertEquals("没有凭据就不该发起登录", 0, api.loginCalls)
    }

    /**
     * 会话 cookie 在但 uid 丢了（旧版本升级、或被系统杀进程打断了重登）：
     * 必须自愈。以前这条路径会停在"登录态但没有 uid"——抽屉头像骨架点不动，
     * 登录页也进不去，只能靠手动登出。
     */
    @Test
    fun coldStartWithCookieButNoUidRecovers() {
        val api = FakeAuthApi()
        val repo = build(
            api = api,
            store = cookieStore(sessionValue = TOKEN),
            credentials = credentialStore().apply { save(EMAIL, PASSWORD) },
        )

        assertEquals(1, api.loginCalls)
        assertEquals(UID.toLong(), repo.currentUserId())
        assertTrue(repo.isLoggedIn())
    }

    /** 有效 cookie 但没有 uid 也没有凭据：无法恢复，必须落回登出态而不是假装登录 */
    @Test
    fun coldStartWithUnusableCookieFallsBackToLoggedOut() {
        val store = cookieStore(sessionValue = TOKEN)
        val repo = build(
            api = FakeAuthApi(),
            store = store,
            credentials = credentialStore(),
        )

        assertFalse(repo.isLoggedIn())
        assertNull(repo.currentUserId())
        assertFalse("无法使用的会话 cookie 必须清掉", hasValidSessionCookie(store))
    }

    // ---- 失效 → 重登 ----

    @Test
    fun sessionClearedTriggersReLoginWithSavedCredentials() {
        val credentials = credentialStore().apply { save(EMAIL, PASSWORD) }
        val api = FakeAuthApi()
        val monitor = SessionMonitor()
        val repo = build(api, cookieStore(sessionValue = TOKEN), credentials, monitor)
        assertTrue(repo.isLoggedIn())

        monitor.notifySessionCleared()

        assertEquals(1, api.loginCalls)
        assertEquals(PASSWORD, api.lastPassword)
    }

    /** 匿名浏览同样会收到空 cookie：没有凭据时不许自作聪明发起登录 */
    @Test
    fun sessionClearedWithoutCredentialsDoesNotLogin() {
        val api = FakeAuthApi()
        val monitor = SessionMonitor()
        val repo = build(api, cookieStore(sessionValue = BLANK), credentialStore(), monitor)

        monitor.notifySessionCleared()

        assertEquals(0, api.loginCalls)
        assertFalse(repo.isLoggedIn())
    }

    /**
     * 冷启动那一次补登失败（例如开机时网络还没起来）之后，后续的空 cookie 必须还能再触发重试。
     * 以前只有"自认为登录中"才重试，于是这一次失败会让整个进程都不再重登——
     * 正好是"会话过期后被迫重输密码"那个要修的毛病。
     */
    @Test
    fun coldStartRetrySurvivesFirstTransientFailure() {
        var nowMs = 0L
        val credentials = credentialStore().apply { save(EMAIL, PASSWORD) }
        val api = FakeAuthApi()
        val monitor = SessionMonitor()
        val store = cookieStore(sessionValue = BLANK)
        api.loginBehaviour = { throw IOException("offline") }
        val repo = build(api, store, credentials, monitor, now = { nowMs })

        assertFalse(repo.isLoggedIn())
        assertEquals(1, api.loginCalls)

        api.loginBehaviour = { LoginResponse(UID) }
        nowMs += 300_000
        monitor.notifySessionCleared()

        assertEquals(2, api.loginCalls)
        assertTrue(repo.isLoggedIn())
        assertEquals("登录成功一次只 +1", 1L, repo.sessionVersion.value)
    }

    // ---- 失败分类 ----

    /**
     * 弱网抖动不该淘汰会话。旧实现里三次 Network 失败就会 clearSession：
     * 删掉保存的密码、把用户踢成登出态。
     */
    @Test
    fun transientFailuresKeepSessionAndCredentials() {
        var nowMs = 0L
        val credentials = credentialStore().apply {
            save(EMAIL, PASSWORD)
            saveUid(UID.toLong())
        }
        val api = FakeAuthApi()
        val monitor = SessionMonitor()
        val repo = build(api, cookieStore(sessionValue = TOKEN), credentials, monitor, now = { nowMs })
        api.loginBehaviour = { throw IOException("offline") }

        repeat(4) {
            monitor.notifySessionCleared()
            nowMs += 300_000
        }

        assertEquals(4, api.loginCalls)
        assertTrue("弱网抖动不该把用户踢成登出态", repo.isLoggedIn())
        assertNotNull("弱网抖动不该删掉保存的密码", credentials.load())
    }

    /** 凭据真的失效时，三次之后必须淘汰并清干净 */
    @Test
    fun definitiveFailuresEvictAfterThreeAttempts() {
        var nowMs = 0L
        val credentials = credentialStore().apply {
            save(EMAIL, PASSWORD)
            saveUid(UID.toLong())
        }
        val api = FakeAuthApi()
        val monitor = SessionMonitor()
        val repo = build(api, cookieStore(sessionValue = TOKEN), credentials, monitor, now = { nowMs })
        api.loginBehaviour = { LoginResponse(RESULT_INVALID) }

        repeat(2) {
            monitor.notifySessionCleared()
            nowMs += 300_000
        }
        assertTrue("两次还不该淘汰", repo.isLoggedIn())

        monitor.notifySessionCleared()

        assertEquals(3, api.loginCalls)
        assertFalse(repo.isLoggedIn())
        assertNull(credentials.load())
    }

    /** 瞬态失败不该占用确定性失败的额度 */
    @Test
    fun transientFailuresDoNotConsumeEvictionBudget() {
        var nowMs = 0L
        val credentials = credentialStore().apply {
            save(EMAIL, PASSWORD)
            saveUid(UID.toLong())
        }
        val api = FakeAuthApi()
        val monitor = SessionMonitor()
        val repo = build(api, cookieStore(sessionValue = TOKEN), credentials, monitor, now = { nowMs })

        api.loginBehaviour = { throw IOException("offline") }
        repeat(5) {
            monitor.notifySessionCleared()
            nowMs += 300_000
        }
        assertTrue(repo.isLoggedIn())

        api.loginBehaviour = { LoginResponse(RESULT_INVALID) }
        repeat(2) {
            monitor.notifySessionCleared()
            nowMs += 300_000
        }
        assertTrue("前面 5 次网络失败不该折算成淘汰额度", repo.isLoggedIn())

        monitor.notifySessionCleared()
        assertFalse(repo.isLoggedIn())
    }

    /** 成功重登后失败计数必须归零，否则会跨会话累计，让下次一次失败就被淘汰 */
    @Test
    fun successfulReLoginResetsEvictionCounter() {
        var nowMs = 0L
        val credentials = credentialStore().apply {
            save(EMAIL, PASSWORD)
            saveUid(UID.toLong())
        }
        val api = FakeAuthApi()
        val monitor = SessionMonitor()
        val repo = build(api, cookieStore(sessionValue = TOKEN), credentials, monitor, now = { nowMs })

        api.loginBehaviour = { LoginResponse(RESULT_INVALID) }
        repeat(2) {
            monitor.notifySessionCleared()
            nowMs += 300_000
        }
        assertTrue("两次还没到淘汰上限", repo.isLoggedIn())

        api.loginBehaviour = { LoginResponse(UID) }
        monitor.notifySessionCleared()
        nowMs += 300_000
        assertTrue(repo.isLoggedIn())

        api.loginBehaviour = { LoginResponse(RESULT_INVALID) }
        repeat(2) {
            monitor.notifySessionCleared()
            nowMs += 300_000
        }
        assertTrue("成功登录后计数应归零，不该累计到淘汰", repo.isLoggedIn())
    }

    // ---- 登出竞态 ----

    @Test
    fun loginResultIsDiscardedWhenUserLogsOutMidFlight() {
        val credentials = credentialStore().apply {
            save(EMAIL, PASSWORD)
            saveUid(UID.toLong())
        }
        val store = cookieStore(sessionValue = TOKEN)
        val api = FakeAuthApi()
        val repo = build(api, store, credentials)
        api.beforeLoginReturns = { repo.logout() }

        val result = runBlocking { repo.login(EMAIL, PASSWORD) }

        assertTrue(result.exceptionOrNull() is LoginError.Cancelled)
        assertFalse("登出后不能被在途的登录请求登回来", repo.isLoggedIn())
        assertNull("登出后不能把凭据写回", credentials.load())
    }

    /**
     * 会话 cookie 是 cookie jar 在 OkHttp 回调之前就落盘的，所以"登出期间返回的登录响应"
     * 会在磁盘上留下一个有效 cookie。只守住内存态不够：下次冷启动会变成
     * "有效 cookie + 无凭据 + 无 uid"的假登录态。
     */
    @Test
    fun logoutDuringLoginLeavesNoValidCookieBehind() {
        val credentials = credentialStore().apply {
            save(EMAIL, PASSWORD)
            saveUid(UID.toLong())
        }
        val store = cookieStore(sessionValue = TOKEN)
        val api = FakeAuthApi()
        val repo = build(api, store, credentials)
        api.beforeLoginReturns = {
            // 顺序必须和真实一致：用户在请求在途时登出，之后 OkHttp 才把响应里的
            // 新会话 cookie 落盘。反过来写 cookie 会被 logout 自己清掉，测不出东西
            repo.logout()
            store.add(URI(BASE), sessionCookie("fresh-token"))
        }

        runBlocking { repo.login(EMAIL, PASSWORD) }

        assertFalse(repo.isLoggedIn())
        assertFalse("登出后不许留下有效会话 cookie", hasValidSessionCookie(store))
    }

    // ---- 会话版本（页面统一的重载信号）----

    /**
     * 冷启动恢复只该 +1：多了会让页面重载两遍。
     * 版本号是 StateFlow，构造完就能读，所以这个能直接断言。
     */
    @Test
    fun coldStartRecoveryBumpsSessionVersionOnce() {
        val api = FakeAuthApi()
        val repo = build(
            api = api,
            store = cookieStore(sessionValue = BLANK),
            credentials = credentialStore().apply { save(EMAIL, PASSWORD) },
        )

        assertEquals(1, api.loginCalls)
        assertEquals("恢复一次只该 +1", 1L, repo.sessionVersion.value)
    }

    /** 会话失效后的重登：登录态没变，页面只能靠版本号自增知道数据该重拉 */
    @Test
    fun expiryReLoginBumpsSessionVersion() {
        val credentials = credentialStore().apply {
            save(EMAIL, PASSWORD)
            saveUid(UID.toLong())
        }
        val monitor = SessionMonitor()
        val repo = build(FakeAuthApi(), cookieStore(sessionValue = TOKEN), credentials, monitor)
        val before = repo.sessionVersion.value

        monitor.notifySessionCleared()

        assertEquals(before + 1, repo.sessionVersion.value)
    }

    @Test
    fun loginAndLogoutEachBumpSessionVersion() {
        val repo = build(FakeAuthApi(), cookieStore(sessionValue = BLANK), credentialStore())
        assertEquals(0L, repo.sessionVersion.value)

        runBlocking { repo.login(EMAIL, PASSWORD) }
        assertEquals(1L, repo.sessionVersion.value)

        repo.logout()
        assertEquals(2L, repo.sessionVersion.value)
    }

    @Test
    fun failedLoginDoesNotBumpSessionVersion() {
        val api = FakeAuthApi()
        val repo = build(api, cookieStore(sessionValue = BLANK), credentialStore())
        api.loginBehaviour = { throw IOException("offline") }

        val result = runBlocking { repo.login(EMAIL, PASSWORD) }

        assertTrue(result.isFailure)
        assertEquals("登录失败不该让页面重载", 0L, repo.sessionVersion.value)
    }

    /** 本来就登出时再清一次不制造版本变化，否则每次无意义清理都会让页面重载 */
    @Test
    fun clearingAnAlreadyEmptySessionDoesNotBumpSessionVersion() {
        val repo = build(FakeAuthApi(), cookieStore(sessionValue = BLANK), credentialStore())

        repo.logout()

        assertEquals(0L, repo.sessionVersion.value)
    }

    /** 会话变化必须真的送到订阅方：生产者（sessionVersion）与消费模式（helper）的接缝 */
    @Test
    fun sessionChangeReachesObserverRegisteredThroughTheHelper() {
        val credentials = credentialStore().apply {
            save(EMAIL, PASSWORD)
            saveUid(UID.toLong())
        }
        val monitor = SessionMonitor()
        val repo = build(FakeAuthApi(), cookieStore(sessionValue = TOKEN), credentials, monitor)
        val reloads = mutableListOf<Unit>()
        val job = collectScope.reloadOnSessionChange(repo.sessionVersion) { reloads += Unit }

        monitor.notifySessionCleared()

        assertEquals("自动重登成功是会话变化，订阅方必须重载一次", 1, reloads.size)
        job.cancel()
    }

    /** 登出同样是会话变化：页面不能继续留着上一个账号的数据 */
    @Test
    fun logoutReachesObserverRegisteredThroughTheHelper() {
        val credentials = credentialStore().apply {
            save(EMAIL, PASSWORD)
            saveUid(UID.toLong())
        }
        val repo = build(FakeAuthApi(), cookieStore(sessionValue = TOKEN), credentials)
        val reloads = mutableListOf<Unit>()
        val job = collectScope.reloadOnSessionChange(repo.sessionVersion) { reloads += Unit }

        repo.logout()

        assertEquals(1, reloads.size)
        job.cancel()
    }

    // ---- 凭据持久化 ----

    @Test
    fun successfulLoginPersistsCredentialsAndUid() {
        val credentials = credentialStore()
        val repo = build(FakeAuthApi(), cookieStore(sessionValue = BLANK), credentials)

        val result = runBlocking { repo.login(EMAIL, PASSWORD) }

        assertTrue(result.isSuccess)
        assertEquals(Credentials(EMAIL, PASSWORD), credentials.load())
        assertEquals(UID.toLong(), repo.currentUserId())
        assertEquals(AuthStatus.LOGGED_IN, repo.authStatus.value)
    }

    // ---- 检测链路 ----

    /** 服务端失效时回的空白 POIPIKU_LK 必须转成 sessionCleared，整条链路的入口在这一句 */
    @Test
    fun blankSessionCookieNotifiesSessionCleared() {
        val monitor = SessionMonitor()
        val jar = PersistentCookieJar(cookieStore(sessionValue = TOKEN), monitor)
        val seen = mutableListOf<Unit>()
        val collector = collectScope.launch { monitor.sessionCleared.collect { seen += it } }

        jar.saveFromResponse(
            BASE.toHttpUrl(),
            listOf(
                Cookie.Builder()
                    .name(SESSION_COOKIE).value("").domain("poipiku.com").path("/").build(),
            ),
        )

        assertEquals(1, seen.size)
        collector.cancel()
    }

    // ---- 工具 ----

    private fun build(
        api: FakeAuthApi,
        store: CookieStore,
        credentials: CredentialStore,
        monitor: SessionMonitor = SessionMonitor(),
        now: () -> Long = { 0L },
    ): AuthRepository = AuthRepository(
        authApi = api.api,
        cookieJar = PersistentCookieJar(store, monitor),
        cookieStore = store,
        sessionMonitor = monitor,
        credentialStore = credentials,
        blockListRepository = BlockListRepository(),
        runtime = SessionRuntime(Dispatchers.Unconfined, now),
    )

    /** [sessionValue] 传空串即复现真实落盘形态：失效回包里的空 POIPIKU_LK */
    private fun cookieStore(sessionValue: String): CookieStore {
        val store = FileCookieStore(tmp.newFile("cookies.properties"))
        store.add(URI(BASE), sessionCookie(sessionValue))
        return store
    }

    private fun sessionCookie(value: String) = HttpCookie(SESSION_COOKIE, value).apply {
        domain = "poipiku.com"
        path = "/"
    }

    private fun hasValidSessionCookie(store: CookieStore): Boolean =
        store.getCookies().any { it.name == SESSION_COOKIE && it.value.isNotBlank() }

    private fun credentialStore(): CredentialStore =
        CredentialStore(FakeStorage(), FakeCipher())

    /**
     * AuthApi 有二十多个方法，逐个手写桩会随接口变动腐化，
     * 这里用动态代理只接住 login，其余一律抛错（生产调用方都包了 runCatching）。
     */
    private class FakeAuthApi {
        var loginCalls = 0
        var lastPassword: String? = null
        var beforeLoginReturns: (() -> Unit)? = null

        /** 抛 IOException 即模拟网络失败；返回负 result 即凭据失效 */
        var loginBehaviour: () -> LoginResponse = { LoginResponse(UID) }

        val api: AuthApi = Proxy.newProxyInstance(
            AuthApi::class.java.classLoader,
            arrayOf(AuthApi::class.java),
        ) { _, method, args ->
            when (method.name) {
                "login" -> {
                    loginCalls++
                    lastPassword = args?.getOrNull(1) as? String
                    beforeLoginReturns?.invoke()
                    loginBehaviour()
                }
                // 资料页与用户主页：昵称解析链路要它们返回真实形状的 Response
                else -> throw UnsupportedOperationException(method.name)
            }
        } as AuthApi
    }

    private class FakeCipher : CredentialCipher {
        override fun encrypt(plain: String): String = "ENC:$plain"
        override fun decrypt(cipherText: String): String =
            cipherText.removePrefix("ENC:")
    }

    private class FakeStorage : CredentialStorage {
        private val map = HashMap<String, String>()
        private val reads = HashMap<String, CountDownLatch>()

        override fun get(key: String): String? {
            reads.getOrPut(key) { CountDownLatch(1) }.countDown()
            return map[key]
        }

        override fun put(key: String, value: String) {
            map[key] = value
        }

        override fun remove(key: String) {
            map.remove(key)
        }

        fun awaitRead(key: String, timeoutMs: Long = 2_000L): Boolean =
            reads.getOrPut(key) { CountDownLatch(1) }.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    private companion object {
        const val EMAIL = "user@example.com"
        const val PASSWORD = "secret123"
        const val TOKEN = "66c3af0de94693f0019be93cffab20b3"
        const val SESSION_COOKIE = "POIPIKU_LK"
        const val BASE = "https://poipiku.com/"
        const val BLANK = ""
        const val UID = 14189264
        const val RESULT_INVALID = -1
        const val KEY_EMAIL = "email_enc"

    }
}
