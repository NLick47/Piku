package com.piku.client.data.repository

import com.piku.client.data.local.CredentialCipher
import com.piku.client.data.local.CredentialStorage
import com.piku.client.data.local.CredentialStore
import com.piku.client.data.local.InMemorySharedPreferences
import com.piku.client.data.local.PopularTagCacheRepository
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.AuthApi
import com.piku.client.data.remote.FileCookieStore
import com.piku.client.data.remote.LoginResponse
import com.piku.client.data.remote.PersistentCookieJar
import com.piku.client.data.remote.PoipikuApi
import com.piku.client.data.remote.SessionMonitor
import com.piku.client.domain.usecase.LoadBlockUsersUseCase
import java.lang.reflect.Proxy
import java.net.CookieStore
import java.net.HttpCookie
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 屏蔽名单预热：**一次会话变化只该拉一轮**。
 * 登录时登录态与 sessionVersion 会同时变，两条路各预热一次就会白拉两轮
 * （每轮最多 5 页），这是本测试要钉住的回归。
 */
class BlockListSyncTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var blockListCalls = 0

    @Test
    fun loginWarmsBlockListExactlyOnce() = runTest {
        val monitor = SessionMonitor()
        val repo = authRepository(monitor)
        val sync = BlockListSync(
            loadBlockUsersUseCase = LoadBlockUsersUseCase(feedRepository(monitor, repo)),
            blockListRepository = BlockListRepository(),
            authRepository = repo,
            runtime = SessionRuntime(StandardTestDispatcher(testScheduler)) { 0L },
        )
        sync.start()
        advanceUntilIdle()
        assertEquals("冷启动未登录，不该预热", 0, blockListCalls)

        repo.login(EMAIL, PASSWORD)
        settle()

        assertTrue("登录后必须预热", blockListCalls >= 1)
        assertEquals("一次登录只该预热一轮", 1, blockListCalls)
    }

    /**
     * 预热里的分页解析跑在 Dispatchers.Default 上，测试调度等不到它；从那边回来的续体
     * 又会排在测试调度器上没人推。所以这里持续 pump + 真实等待一小段，
     * 让"第一轮"和"可能存在的第二轮"都真的跑出来再断言。
     */
    private suspend fun TestScope.settle(ms: Long = SETTLE_MS) {
        val until = System.nanoTime() + ms * 1_000_000L
        while (System.nanoTime() < until) {
            advanceUntilIdle()
            Thread.sleep(10)
        }
    }

    // ---- 工具 ----

    private fun authRepository(monitor: SessionMonitor): AuthRepository {
        val store = emptyCookieStore()
        return AuthRepository(
        authApi = AuthApiProxy().api,
        cookieJar = PersistentCookieJar(store, monitor),
        cookieStore = store,
        sessionMonitor = monitor,
        credentialStore = CredentialStore(FakeStorage(), FakeCipher()),
        blockListRepository = BlockListRepository(),
        runtime = SessionRuntime(Dispatchers.Unconfined) { 0L },
        )
    }

    /** 屏蔽名单页：返回一个空列表页，预热一轮就结束（计数用 getBlockList 调用次数） */
    private fun feedRepository(monitor: SessionMonitor, auth: AuthRepository): FeedRepository =
        FeedRepository(
            api = blockListApi(),
            settingsRepository = SettingsRepository(InMemorySharedPreferences()),
            authRepository = auth,
            sessionMonitor = monitor,
            popularTagCacheRepository = PopularTagCacheRepository(InMemorySharedPreferences()),
        )

    private fun blockListApi(): PoipikuApi = Proxy.newProxyInstance(
        PoipikuApi::class.java.classLoader,
        arrayOf(PoipikuApi::class.java),
    ) { _, method, _ ->
        if (method.name == "getBlockList") {
            blockListCalls++
            "".toResponseBody("text/html".toMediaTypeOrNull())
        } else {
            throw UnsupportedOperationException(method.name)
        }
    } as PoipikuApi

    private fun emptyCookieStore(): CookieStore {
        val store = FileCookieStore(tmp.newFile("cookies.properties"))
        store.add(
            URI("https://poipiku.com/"),
            HttpCookie("POIPIKU_LK", "").apply {
                domain = "poipiku.com"
                path = "/"
            },
        )
        return store
    }

    private class AuthApiProxy {
        val api: AuthApi = Proxy.newProxyInstance(
            AuthApi::class.java.classLoader,
            arrayOf(AuthApi::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "login" -> LoginResponse(UID)
                "getMyEditSetting", "getUserTop" ->
                    retrofit2.Response.success(
                        "".toResponseBody("text/html".toMediaTypeOrNull()),
                    )
                else -> throw UnsupportedOperationException(method.name)
            }
        } as AuthApi
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
        const val UID = 14189264
        const val SETTLE_MS = 500L
    }
}
