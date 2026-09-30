package com.piku.client.data.auth

import android.util.Log
import com.piku.client.R
import com.piku.client.data.remote.apiCall
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.AuthStatus
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.SourceAccount
import com.piku.client.domain.source.SourceAuth
import com.piku.client.domain.source.SourceAuthRoutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PixivAuthRepository @Inject constructor(
    private val api: PixivAuthApi,
    private val store: PixivAuthStore,
    private val endpoints: PixivAuthEndpoints,
    private val runtime: PixivAuthRuntime,
) : SourceAuth {

    override val source = WorkSource.PIXIV

    override val loginRoute: String = SourceAuthRoutes.PIXIV_LOGIN

    override val logoutMessageRes = R.string.account_logout_message

    private val _status = MutableStateFlow(
        if (store.current() != null) AuthStatus.LOGGED_IN else AuthStatus.LOGGED_OUT,
    )
    override val status: StateFlow<AuthStatus> = _status.asStateFlow()

    private val _account = MutableStateFlow(store.current()?.toAccount())
    override val account: StateFlow<SourceAccount?> = _account.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + runtime.dispatcher)

    /** 串行化「换令牌 / 刷新」：两个都可能在途，后到的响应不该盖掉先到的 */
    private val tokenMutex = Mutex()

    /** 每次登出自增：在途的刷新回来后据此丢弃结果，不把令牌写回已登出的会话 */
    private val sessionEpoch = AtomicInteger(0)

    private var refreshJob: Job? = null

    init {
        store.current()?.let(::scheduleRefresh)
    }

    /** 登录页地址：直连 pixiv 应用接口域，不经任何 Cloudflare 中继（cf 出网 IP 被 pixiv 挡死） */
    fun loginPageUrl(codeChallenge: String): String = endpoints.loginPageUrl(codeChallenge)

    /** 授权码换令牌。成功即登录态生效并落盘 */
    suspend fun completeLogin(code: String, codeVerifier: String): Result<Unit> =
        withContext(runtime.dispatcher) {
            val epoch = sessionEpoch.get()
            tokenMutex.withLock {
                exchange(endpoints.authorizationCodeFields(code, codeVerifier))
                    .mapCatching { token ->
                        // 换令牌期间用户已登出：结果作废，不写回登录态
                        if (sessionEpoch.get() != epoch) throw PixivAuthError.Cancelled
                        adopt(token)
                    }
            }
        }

    override fun logout() {
        Log.d(TAG, "pixiv logout")
        sessionEpoch.incrementAndGet()
        refreshJob?.cancel()
        refreshJob = null
        store.clear()
        _account.value = null
        _status.value = AuthStatus.LOGGED_OUT
    }

    /**
     * 换令牌。**必须留下失败的可诊断信息**：这个端点的失败原因（invalid_grant /
     * invalid_client / 被挡 / 太频繁）全在响应的状态码与正文里，丢掉就只剩一句
     * "登录失败"，用户和开发者都无从下手。
     */
    private suspend fun exchange(fields: Map<String, String>): Result<PixivToken> {
        // 只记形状不记秘密：够判断 code/verifier 有没有取错
        Log.d(
            TAG,
            "pixiv token request: grant=${fields["grant_type"]} " +
                "codeLen=${fields["code"]?.length ?: 0} " +
                "verifierLen=${fields["code_verifier"]?.length ?: 0} " +
                "redirect=${fields["redirect_uri"] ?: "-"}",
        )
        val signature = endpoints.clientSignature(runtime.now())
        val response = apiCall {
            api.token(fields, clientTime = signature.time, clientHash = signature.hash)
        }.getOrElse { error ->
            Log.w(TAG, "pixiv token call failed: ${error::class.simpleName}")
            return Result.failure(error.toAuthError())
        }
        if (!response.isSuccessful) {
            val raw = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
            Log.w(TAG, "pixiv token HTTP ${response.code()}: ${raw.take(BODY_LOG_LIMIT)}")
            return Result.failure(httpAuthError(response.code()))
        }
        val body = response.body()
        if (body == null) {
            Log.w(TAG, "pixiv token empty body (HTTP ${response.code()})")
            return Result.failure(PixivAuthError.Unknown)
        }
        if (body.error.isNotBlank()) Log.w(TAG, "pixiv token rejected: ${body.error}")
        return runCatching { body.toToken(runtime.now()) }
            .recoverCatching { throw it.toAuthError() }
    }

    private fun adopt(token: PixivToken) {
        store.save(token)
        _account.value = token.toAccount()
        _status.value = AuthStatus.LOGGED_IN
        scheduleRefresh(token)
        // 头像只记"有没有/是不是默认图"：头像不显示时，这一行就能区分是没拿到 URL 还是图拉不下来
        val avatar = token.avatarUrl
        Log.d(
            TAG,
            "pixiv token adopted: uid=${token.userId} ttl=${token.expiresAt - runtime.now()}ms " +
                "avatar=${when {
                    avatar == null -> "none"
                    avatar.contains("default_user") -> "default"
                    else -> "custom"
                }}",
        )
    }

    /**
     * 到期前一分钟换新的：用户不该撞上"正好在过期那一秒发请求"。
     *
     * 注意刷新成功后是**站在刷新任务里**排下一次的，所以这里 cancel 掉的常常就是当前任务自己——
     * 那一刻它已经没有挂起点了，取消等于空转，新任务照常排上。
     */
    private fun scheduleRefresh(token: PixivToken) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val wait = token.expiresAt - PixivToken.EXPIRY_MARGIN_MS - runtime.now()
            if (wait > 0) delay(wait)
            refresh()
        }
    }

    private suspend fun refresh() {
        var rejected = false
        tokenMutex.withLock {
            val current = store.current() ?: return@withLock
            val epoch = sessionEpoch.get()
            exchange(endpoints.refreshTokenFields(current.refreshToken))
                .onSuccess { token -> if (sessionEpoch.get() == epoch) adopt(token) }
                .onFailure { error ->
                    if (sessionEpoch.get() == epoch) rejected = error is PixivAuthError.CredentialRejected
                }
        }
        // 只有服务端明确拒绝这笔 refresh_token 才登出：网络抖动不该删掉用户的登录
        if (rejected) {
            Log.d(TAG, "pixiv refresh rejected by server, logging out")
            logout()
        }
    }

    private companion object {
        const val TAG = "PikuDiag"

        /** 错误正文只留开头一段：够看清 error 字段，又不至于把日志灌满 */
        const val BODY_LOG_LIMIT = 300
    }
}

/**
 * 响应 → 令牌。`error` 字段非空 = 服务端拒绝（授权码过期、grant 不合法），
 * 这是 HTTP 200 下的失败，不看它就会把空令牌当成登录成功。
 */
internal fun PixivTokenResponse.toToken(now: Long): PixivToken {
    if (error.isNotBlank()) throw PixivAuthError.CredentialRejected
    if (accessToken.isBlank() || refreshToken.isBlank()) throw PixivAuthError.Unknown
    return PixivToken(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = now + expiresIn * 1000L,
        userId = user.id,
        name = user.name,
        account = user.account,
        avatarUrl = user.profileImageUrls.px170.takeIf { it.isNotBlank() },
    )
}

/** 令牌 → 外壳要看的账号投影。昵称缺失时退到 @账号名，别给一行空白 */
internal fun PixivToken.toAccount(): SourceAccount = SourceAccount(
    displayName = name.ifBlank { account },
    account = account,
    avatarUrl = avatarUrl,
)

/** 传输/解析层的错误归一到登录语义。4xx = 凭据被拒，其余都当作可重试 */
internal fun Throwable.toAuthError(): PixivAuthError = when (this) {
    is PixivAuthError -> this
    is AppError.Network -> PixivAuthError.Network
    is AppError.Http -> httpAuthError(code)
    else -> PixivAuthError.Unknown
}

/**
 * 状态码 → 语义。全都归成"凭据被拒"是最省事也最坑人的做法：
 * 403 是出口被挡、429 是太频繁，用户照"重新登录"去做只会一遍遍撞同一堵墙。
 */
internal fun httpAuthError(code: Int): PixivAuthError = when (code) {
    400, 401 -> PixivAuthError.CredentialRejected
    403 -> PixivAuthError.Blocked
    429 -> PixivAuthError.RateLimited
    else -> PixivAuthError.Unknown
}
