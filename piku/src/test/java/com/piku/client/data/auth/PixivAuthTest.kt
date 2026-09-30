package com.piku.client.data.auth

import com.piku.client.data.local.CredentialCipher
import com.piku.client.data.local.CredentialStorage
import com.piku.client.data.local.CredentialStore
import com.piku.client.data.local.Credentials
import com.piku.client.data.remote.PikuJson
import com.piku.client.domain.model.AuthStatus
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.SourceAuthRoutes
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.Instant

/** 服务端给的失败正文长这样；测试要证明它被读到并按状态码分了类 */
private const val ERROR_BODY = """{"error":"invalid_grant","error_description":"Invalid grant"}"""

class PixivAuthTest {

    @Test
    fun loginPageGoesStraightToPixiv() {
        val url = PixivAuthEndpoints().loginPageUrl("CHALLENGE")

        assertTrue(url.startsWith("https://app-api.pixiv.net/web/v1/login?"))
        assertTrue(url.contains("code_challenge=CHALLENGE"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("client=pixiv-android"))
        assertFalse("登录页不得经 CF 中继", url.contains("pic-relay") || url.contains("pages.dev"))
    }

    /** 自建的非 CF 回源可以整体替换登录页源；默认值是唯一被写死的地方 */
    @Test
    fun loginOriginIsSwappable() {
        val url = PixivAuthEndpoints(loginOrigin = "https://vps.example.com/pxapi").loginPageUrl("C")

        assertTrue(url.startsWith("https://vps.example.com/pxapi/web/v1/login?"))
    }

    @Test
    fun tokenRequestFieldsFollowThePixivContract() {
        val endpoints = PixivAuthEndpoints()

        val code = endpoints.authorizationCodeFields("CODE", "VERIFIER")
        assertEquals("authorization_code", code["grant_type"])
        assertEquals("CODE", code["code"])
        assertEquals("VERIFIER", code["code_verifier"])
        assertEquals(
            "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback",
            code["redirect_uri"],
        )
        assertEquals(PixivAuthEndpoints.PIXIV_TOKEN_ORIGIN + "/", endpoints.tokenBaseUrl())

        val refresh = endpoints.refreshTokenFields("REFRESH")
        assertEquals("refresh_token", refresh["grant_type"])
        assertEquals("REFRESH", refresh["refresh_token"])
        assertEquals(code["client_id"], refresh["client_id"])
    }

    // ---------------- 响应映射 ----------------

    @Test
    fun tokenResponseParsesRealPayload() {
        val json = """
            {"access_token":"AT","refresh_token":"RT","expires_in":3600,
             "user":{"id":12345,"name":"ぴく","account":"piku_user",
             "profile_image_urls":{"px_16x16":"https://i.pximg.net/16.jpg",
                                   "px_170x170":"https://i.pximg.net/170.jpg"}}}
        """.trimIndent()

        val token = PikuJson.decodeFromString(PixivTokenResponse.serializer(), json).toToken(1_000L)

        assertEquals("AT", token.accessToken)
        assertEquals("RT", token.refreshToken)
        assertEquals(1_000L + 3_600_000L, token.expiresAt)
        // id 有时是数字有时是字符串，两种都要收下
        assertEquals("12345", token.userId)
        assertEquals("piku_user", token.account)
        assertEquals("https://i.pximg.net/170.jpg", token.avatarUrl)
        assertEquals("ぴく", token.toAccount().displayName)
    }

    /** 到期判定要留一分钟余量，别让请求正好撞在过期那一秒 */
    @Test
    fun expiryCheckKeepsAMargin() {
        val token = PixivToken(accessToken = "a", refreshToken = "r", expiresAt = 600_000L)

        assertFalse(token.isExpiring(now = 0L))
        assertTrue(token.isExpiring(now = 600_000L - PixivToken.EXPIRY_MARGIN_MS))
        assertTrue(token.isExpiring(now = 600_000L))
    }

    @Test
    fun errorFieldMeansRejectedEvenOnHttp200() {
        val response = PikuJson.decodeFromString(
            PixivTokenResponse.serializer(),
            """{"error":"invalid_grant"}""",
        )

        assertThrows(PixivAuthError.CredentialRejected::class.java) { response.toToken(0L) }
    }

    @Test
    fun emptyTokenIsNotASuccess() {
        val response = PixivTokenResponse(accessToken = "", refreshToken = "r", expiresIn = 1)

        assertThrows(PixivAuthError.Unknown::class.java) { response.toToken(0L) }
    }

    @Test
    fun accountFallsBackToLoginNameWhenNameIsBlank() {
        val token = PixivToken(
            accessToken = "a",
            refreshToken = "r",
            expiresAt = 0L,
            account = "piku_user",
        )

        assertEquals("piku_user", token.toAccount().displayName)
    }

    // ---------------- 鉴权头只发给应用接口 ----------------

    @Test
    fun bearerOnlyGoesToAppApiHost() {
        assertEquals(
            listOf("Authorization" to "Bearer TOKEN"),
            pixivAuthHeaders(PixivAuthEndpoints.PIXIV_APP_API_HOST, "TOKEN"),
        )
        assertTrue(pixivAuthHeaders("www.pixiv.net", "TOKEN").isEmpty())
        assertTrue(pixivAuthHeaders(PixivAuthEndpoints.PIXIV_APP_API_HOST, null).isEmpty())
        assertTrue(pixivAuthHeaders(PixivAuthEndpoints.PIXIV_APP_API_HOST, "  ").isEmpty())
    }

    // ---------------- 凭据存储：与 poipiku 完全隔离 ----------------

    @Test
    fun tokenSurvivesRestart() {
        val storage = InMemoryStorage()
        PixivAuthStore(storage, FakeCipher(), PikuJson).save(token())

        // 重新构造 = 冷启动，只能靠密文恢复
        val restored = PixivAuthStore(storage, FakeCipher(), PikuJson)

        assertEquals("AT", restored.accessToken())
        assertEquals("12345", restored.current()?.userId)
    }

    @Test
    fun clearingPixivCredentialsLeavesPoipikuSessionIntact() {
        val storage = InMemoryStorage()
        val pixiv = PixivAuthStore(storage, FakeCipher(), PikuJson)
        val poipiku = CredentialStore(storage, FakeCipher())
        poipiku.save("user@example.com", "secret")
        pixiv.save(token())

        pixiv.clear()

        assertNull(pixiv.accessToken())
        assertEquals(Credentials("user@example.com", "secret"), poipiku.load())
    }

    @Test
    fun clearingPoipikuCredentialsLeavesPixivTokenIntact() {
        val storage = InMemoryStorage()
        val pixiv = PixivAuthStore(storage, FakeCipher(), PikuJson)
        val poipiku = CredentialStore(storage, FakeCipher())
        poipiku.save("user@example.com", "secret")
        pixiv.save(token())

        poipiku.clear()

        assertEquals("AT", pixiv.accessToken())
    }

    @Test
    fun corruptedCipherTextMeansLoggedOut() {
        val storage = InMemoryStorage().apply { put("pixiv_token_enc", "garbage") }

        assertNull(PixivAuthStore(storage, FakeCipher(), PikuJson).accessToken())
    }

    // ---------------- 仓库：登录与登出 ----------------

    @Test
    fun completeLoginAdoptsTokenAndFlipsStatus() = runTest {
        val store = PixivAuthStore(InMemoryStorage(), FakeCipher(), PikuJson)
        val repository = repository(store = store, now = 500L)

        assertEquals(WorkSource.PIXIV, repository.source)
        assertEquals(SourceAuthRoutes.PIXIV_LOGIN, repository.loginRoute)
        assertFalse(repository.isLoggedIn())

        val result = repository.completeLogin(code = "CODE", codeVerifier = "VERIFIER")

        assertTrue(result.isSuccess)
        assertTrue(repository.isLoggedIn())
        assertEquals(AuthStatus.LOGGED_IN, repository.status.value)
        assertEquals("AT", store.accessToken())
        assertEquals(500L + 3_600_000L, store.current()?.expiresAt)
        assertEquals("piku_user", repository.account.value?.account)

        repository.logout()
    }

    @Test
    fun rejectedLoginKeepsLightOff() = runTest {
        val repository = repository(api = FakeApi(PixivTokenResponse(error = "invalid_grant")))

        val result = repository.completeLogin(code = "CODE", codeVerifier = "VERIFIER")

        assertTrue(result.isFailure)
        assertEquals(PixivAuthError.CredentialRejected, result.exceptionOrNull())
        assertFalse(repository.isLoggedIn())
        assertEquals(AuthStatus.LOGGED_OUT, repository.status.value)
    }

    @Test
    fun networkFailureIsRetryableNotRejected() = runTest {
        val api = FakeApi(PixivTokenResponse()).apply { failure = IOException("no route") }
        val repository = repository(api = api)

        val result = repository.completeLogin(code = "CODE", codeVerifier = "VERIFIER")

        assertEquals(PixivAuthError.Network, result.exceptionOrNull())
        assertFalse(repository.isLoggedIn())
    }

    /**
     * 失败要按状态码分语义。全都说成"凭据被拒"会让用户对着 403/429 一遍遍重登，
     * 而真正的理由是出口被挡或太频繁——这两种只能靠换网络 / 等一会儿解决。
     */
    @Test
    fun httpStatusDeterminesTheKindOfFailure() = runTest {
        suspend fun failureOf(code: Int): Throwable? = repository(
            api = FakeApi(PixivTokenResponse(), errorCode = code),
        ).completeLogin(code = "CODE", codeVerifier = "VERIFIER").exceptionOrNull()

        assertEquals(PixivAuthError.CredentialRejected, failureOf(400))
        assertEquals(PixivAuthError.CredentialRejected, failureOf(401))
        assertEquals(PixivAuthError.Blocked, failureOf(403))
        assertEquals(PixivAuthError.RateLimited, failureOf(429))
        assertEquals(PixivAuthError.Unknown, failureOf(500))
    }

    /** 错误正文被读出来就不会是"未知错误"：这条路径正是线上报"拒绝了登录"时走的 */
    @Test
    fun nonSuccessResponseIsNotTreatedAsSuccess() = runTest {
        val store = PixivAuthStore(InMemoryStorage(), FakeCipher(), PikuJson)
        val repository = repository(api = FakeApi(PixivTokenResponse(), errorCode = 400), store = store)

        val result = repository.completeLogin(code = "CODE", codeVerifier = "VERIFIER")

        assertTrue(result.isFailure)
        assertFalse("失败响应绝不能留下登录态", repository.isLoggedIn())
        assertNull(store.accessToken())
    }

    @Test
    fun logoutClearsTokenAndAccount() = runTest {
        val store = PixivAuthStore(InMemoryStorage(), FakeCipher(), PikuJson)
        val repository = repository(store = store)
        repository.completeLogin(code = "CODE", codeVerifier = "VERIFIER")

        repository.logout()

        assertNull(store.accessToken())
        assertNull(repository.account.value)
        assertFalse(repository.isLoggedIn())
    }

    /**
     * 算法用社区公开用例钉死：时间串 `2019-09-02T20:51:57+02:00` 对应的哈希必须是 5bb0b1ec…。
     * 算错了 pixiv 只回一句"客户端凭据不合法"，根本看不出是哈希错。
     */
    @Test
    fun clientHashMatchesThePublishedVector() {
        assertEquals(
            "5bb0b1ec0b6e1a86d7dc18dbea2c80bf",
            pixivClientHash("2019-09-02T20:51:57+02:00"),
        )
    }

    /** 时间格式：UTC、带 Z、没有小数秒——格式一变哈希就对不上（哈希算的是这个字符串本身） */
    @Test
    fun clientTimeIsUtcWithZAndNoFraction() {
        assertEquals("1970-01-01T00:00:00Z", PixivAuthEndpoints().clientSignature(0L).time)
        assertEquals(
            "2026-10-01T00:00:00Z",
            PixivAuthEndpoints()
                .clientSignature(Instant.parse("2026-10-01T00:00:00Z").toEpochMilli())
                .time,
        )
    }

    /** 两个头必须自洽：哈希算的就是随头一起发出去的那个时间串 */
    @Test
    fun signatureHashCoversTheTimeItShipsWith() {
        val signature = PixivAuthEndpoints().clientSignature(0L)

        assertEquals(pixivClientHash(signature.time), signature.hash)
    }

    /** 换令牌那一发必须带上签名：这两个头是必填的 */
    @Test
    fun tokenRequestCarriesTheClientSignature() = runTest {
        val api = FakeApi(ok())
        val store = PixivAuthStore(InMemoryStorage(), FakeCipher(), PikuJson)
        val repository = repository(api = api, store = store, now = 1_700_000_000_000L)

        repository.completeLogin(code = "CODE", codeVerifier = "VERIFIER")

        assertEquals(
            PixivAuthEndpoints().clientSignature(1_700_000_000_000L),
            api.signatures.last(),
        )
        repository.logout()
    }

    /** 加密不可用时本次会话照常可用（内存里），但不落盘——冷启动自然要求重新登录 */
    @Test
    fun failedEncryptionKeepsSessionInMemoryWithoutPersisting() {
        val storage = InMemoryStorage()
        val store = PixivAuthStore(storage, AlwaysFailCipher(), PikuJson)

        store.save(token())

        assertEquals("AT", store.accessToken())
        // 同一份盘上一个字都没写：换正常密钥重建也读不出东西
        assertNull(PixivAuthStore(storage, FakeCipher(), PikuJson).accessToken())
    }

    private fun TestScope.repository(
        api: FakeApi = FakeApi(ok()),
        store: PixivAuthStore = PixivAuthStore(InMemoryStorage(), FakeCipher(), PikuJson),
        now: Long = 1_000L,
    ): PixivAuthRepository = PixivAuthRepository(
        api = api,
        store = store,
        endpoints = PixivAuthEndpoints(),
        runtime = PixivAuthRuntime(StandardTestDispatcher(testScheduler), now = { now }),
    )

    private fun ok() = PixivTokenResponse(
        accessToken = "AT",
        refreshToken = "RT",
        expiresIn = 3600,
        user = PixivAccount(id = "12345", name = "ぴく", account = "piku_user"),
    )

    private fun token() = PixivToken(
        accessToken = "AT",
        refreshToken = "RT",
        expiresAt = 9_999L,
        userId = "12345",
        name = "ぴく",
        account = "piku_user",
    )

    private class FakeApi(
        var response: PixivTokenResponse,
        /** 非 null 时回这个状态码的错误响应（走真实 HTTP 失败路径） */
        var errorCode: Int? = null,
    ) : PixivAuthApi {
        var failure: IOException? = null
        val requests = mutableListOf<Map<String, String>>()
        val signatures = mutableListOf<PixivClientSignature>()

        override suspend fun token(
            fields: Map<String, String>,
            clientTime: String,
            clientHash: String,
        ): Response<PixivTokenResponse> {
            requests += fields
            signatures += PixivClientSignature(clientTime, clientHash)
            failure?.let { throw it }
            val code = errorCode
            return if (code == null) {
                Response.success(response)
            } else {
                Response.error(code, ERROR_BODY.toResponseBody("application/json".toMediaType()))
            }
        }
    }

    private class FakeCipher : CredentialCipher {
        override fun encrypt(plain: String): String = "ENC:$plain"
        override fun decrypt(cipherText: String): String {
            require(cipherText.startsWith("ENC:")) { "corrupted" }
            return cipherText.removePrefix("ENC:")
        }
    }

    private class AlwaysFailCipher : CredentialCipher {
        override fun encrypt(plain: String): String = throw IllegalStateException("keystore unavailable")
        override fun decrypt(cipherText: String): String = throw IllegalStateException("keystore unavailable")
    }

    private class InMemoryStorage : CredentialStorage {
        private val map = HashMap<String, String>()
        override fun get(key: String): String? = map[key]
        override fun put(key: String, value: String) {
            map[key] = value
        }

        override fun remove(key: String) {
            map.remove(key)
        }
    }
}
