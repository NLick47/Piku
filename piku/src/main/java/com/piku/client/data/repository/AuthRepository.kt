package com.piku.client.data.repository

import android.util.Log
import com.piku.client.data.local.CredentialStore
import com.piku.client.data.remote.ApiConfig
import com.piku.client.data.remote.AuthApi
import com.piku.client.data.remote.SessionMonitor
import com.piku.client.data.remote.apiCall
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.AuthStatus
import com.piku.client.domain.model.LoginError
import com.piku.client.domain.model.RegisterError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.CookieStore
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val cookieJar: CookieJar,
    private val cookieStore: CookieStore,
    private val sessionMonitor: SessionMonitor,
    private val credentialStore: CredentialStore,
    /** 屏蔽名单随账号存在，清除会话时必须一并清掉 */
    private val blockListRepository: BlockListRepository,
    /** 会话状态的调度与时钟，单测靠它注入确定性的时间和调度 */
    private val runtime: SessionRuntime,
) {

    private val _authStatus =
        MutableStateFlow(if (hasSession()) AuthStatus.LOGGED_IN else AuthStatus.LOGGED_OUT)
    val authStatus: StateFlow<AuthStatus> = _authStatus.asStateFlow()

    /**
     * 会话版本号：登录成功、登出、自动重登成功都会 +1，页面统一观察它决定重载。
     * 用 StateFlow 而不是一次性事件：晚订阅也能看到当前值，不会静默丢；
     * 也不再需要各页面自己比较"登录态变没变"
     */
    private val _sessionVersion = MutableStateFlow(0L)
    val sessionVersion: StateFlow<Long> = _sessionVersion.asStateFlow()

    @Volatile
    private var uid: Long? = null

    private val scope = CoroutineScope(SupervisorJob() + runtime.dispatcher)

    /**
     * 串行化所有登录尝试：手动登录与自动重登不能同时在途，否则后到的响应会盖掉先到的账号。
     * 登录路径都在 runtime.dispatcher 上，所以 ReloginPolicy 只被这一条线碰
     */
    private val sessionMutex = Mutex()
    private val reloginPolicy = ReloginPolicy()

    /** 每次清除会话自增：在途的登录请求据此判定期间是否已登出 */
    private val sessionEpoch = AtomicInteger(0)

    init {
        val restoredUid = if (hasSession()) credentialStore.loadUid() else null
        if (restoredUid != null) {
            uid = restoredUid
            Log.d(TAG, "cold start: restored uid=$uid")
        } else {
            // 有 cookie 但没 uid（数据不一致，或被系统杀进程打断了重登），
            // 以及 cookie 已被服务端作废（失效回包里的空 cookie 会落盘）都走恢复：
            // 没凭据的会被 autoReLogin 清成登出态，不会留下"假登录"
            if (hasSession()) Log.d(TAG, "cold start: session cookie without uid, recovering")
            scope.launch { autoReLogin() }
        }
        scope.launch {
            sessionMonitor.sessionCleared.collect {
                // 匿名浏览同样会收到空 cookie：没有保存的凭据就不要自作聪明
                if (isLoggedIn() || hasSavedCredentials()) autoReLogin()
            }
        }
    }

    fun isLoggedIn(): Boolean = _authStatus.value == AuthStatus.LOGGED_IN

    fun currentUserId(): Long? = uid

    suspend fun login(email: String, password: String): Result<Unit> =
        withContext(runtime.dispatcher) {
            sessionMutex.withLock { loginLocked(email, password) }
        }

    private suspend fun loginLocked(email: String, password: String): Result<Unit> {
        // 登录在途期间用户可能已登出：回来后据此丢弃结果，不写回登录态与凭据
        val epoch = sessionEpoch.get()
        val response = apiCall { authApi.login(email, password) }
        return response.fold(
            onSuccess = { login ->
                Log.d(TAG, "login response: result=${login.result}")
                when {
                    login.result == RESULT_LOCKED -> Result.failure(LoginError.Locked)
                    login.result < 0 -> Result.failure(LoginError.InvalidCredentials)
                    sessionEpoch.get() != epoch -> {
                        // 响应里的会话 cookie 是 cookie jar 在回调之前就落盘的，必须清掉，
                        // 否则磁盘上会留下"有效 cookie + 无凭据"，冷启动就成了假登录态
                        Log.d(TAG, "login ok but session cleared meanwhile, discard")
                        clearSession()
                        Result.failure(LoginError.Cancelled)
                    }
                    else -> {
                        uid = login.result.toLong()
                        _authStatus.value = AuthStatus.LOGGED_IN
                        credentialStore.save(email, password)
                        credentialStore.saveUid(login.result.toLong())
                        // 响应里的会话 cookie 由 cookie jar 在回调之前就落盘了，
                        // 若这期间用户登出，整体回滚（clearSession 会连 cookie 一起清掉），
                        // 否则磁盘上会留下"有效 cookie + 无凭据"，冷启动就成了假登录态
                        if (sessionEpoch.get() != epoch) {
                            Log.d(TAG, "session cleared while writing back, rollback")
                            clearSession()
                            Result.failure(LoginError.Cancelled)
                        } else {
                            // 会话已重建，重登的失败计数与退避都归零
                            reloginPolicy.onSessionEstablished()
                            _sessionVersion.update { it + 1 }
                            Log.d(TAG, "login ok, uid=${login.result} session=${hasSession()}")
                            Result.success(Unit)
                        }
                    }
                }
            },
            onFailure = { error ->
                Log.d(TAG, "login failure: $error")
                Result.failure(
                    if (error is AppError.Network) LoginError.Network else LoginError.Unknown,
                )
            },
        )
    }

    /**
     * 注册（官方 App 的无验证码接口）：
     * 1. GET LoginFormEmailV.jsp 拿每次都会轮换的新 TK 令牌；
     * 2. POST /api/RegistUserF.jsp 提交 NN/EM/PW/TK；
     * 3. 成功后该请求已下发会话 cookie，再走一次登录补全 uid 与 profile。
     */
    suspend fun register(email: String, password: String, nickname: String): Result<Unit> {
        val token = apiCall {
            val form = authApi.getRegisterForm()
            val html = form.body()?.string().orEmpty()
            parseRegisterToken(html) ?: throw IllegalStateException("register TK not found")
        }.getOrElse { error ->
            Log.d(TAG, "register: token fetch failed: $error")
            return Result.failure(
                if (error is AppError.Network) RegisterError.Network else RegisterError.Unknown,
            )
        }
        Log.d(TAG, "register: token len=${token.length}")
        val response = apiCall {
            authApi.register(nickname, email, password, token, X_REQUESTED_WITH)
        }
        return response.fold(
            onSuccess = { reg ->
                Log.d(TAG, "register response: result=${reg.result}")
                when {
                    reg.result > 0 -> {
                        Log.d(TAG, "register ok, logging in to complete session")
                        login(email, password)
                    }
                    reg.result == RESULT_EMAIL_USED -> Result.failure(RegisterError.EmailInUse)
                    reg.result == RESULT_INVALID_NICKNAME -> Result.failure(RegisterError.InvalidNickname)
                    reg.result == RESULT_INVALID_EMAIL -> Result.failure(RegisterError.InvalidEmail)
                    else -> Result.failure(RegisterError.Unknown)
                }
            },
            onFailure = { error ->
                Log.d(TAG, "register failure: $error")
                Result.failure(
                    if (error is AppError.Network) RegisterError.Network else RegisterError.Unknown,
                )
            },
        )
    }

    fun logout() {
        Log.d(TAG, "logout")
        clearSession()
    }

    /**
     * 会话失效（或冷启动没拿到有效会话）后的自动重登：
     * - 无保存凭据 → 自认为登录中则登出，否则什么都不做
     * - 在防抖/退避窗口内 → 跳过，避免错误响应反复触发
     * - 凭据失效或账号锁定连续多次 → 清除凭据并登出
     * - 网络等瞬态失败 → 只拉长退避，不淘汰会话（弱网抖动不该删掉用户密码）
     */
    private suspend fun autoReLogin() = sessionMutex.withLock<Unit> {
        if (!reloginPolicy.allowAttempt(runtime.now())) return@withLock
        val credentials = credentialStore.load()
        if (credentials == null) {
            Log.d(TAG, "auto re-login: no saved credentials")
            if (isLoggedIn()) clearSession()
            return@withLock
        }
        loginLocked(credentials.email, credentials.password)
            .onSuccess { Log.d(TAG, "auto re-login ok") }
            .onFailure { error ->
                // 在途期间用户已登出：login 已作废结果，这里不再计数
                if (!isLoggedIn()) return@onFailure
                Log.d(TAG, "auto re-login failed: $error")
                if (reloginPolicy.onFailure(error)) clearSession()
            }
    }

    private fun hasSavedCredentials(): Boolean = credentialStore.load() != null

    /** 清除 cookie、凭据与登录态 */
    private fun clearSession() {
        val hadSession = isLoggedIn() || uid != null
        sessionEpoch.incrementAndGet()
        cookieStore.removeAll()
        credentialStore.clear()
        uid = null
        _authStatus.value = AuthStatus.LOGGED_OUT
        // 匿名状态下的空清不制造版本变化，否则每次无意义清理都会让页面重载
        if (hadSession) _sessionVersion.update { it + 1 }
        // 屏蔽名单属于账号：不清掉的话，换账号登录后内存名单会把新账号的内容误过滤。
        // 在途的预热分页由 BlockListSync 自己按登录态作废
        blockListRepository.clear()
    }

    private fun hasSession(): Boolean {
        val session = cookieJar.loadForRequest(ApiConfig.BASE_URL.toHttpUrl())
            .any { it.name == SESSION_COOKIE && it.value.isNotBlank() }
        Log.d(TAG, "hasSession: $session")
        return session
    }

    private companion object {
        const val SESSION_COOKIE = "POIPIKU_LK"
        const val RESULT_LOCKED = -21
        const val RESULT_EMAIL_USED = -8
        const val RESULT_INVALID_NICKNAME = -6
        const val RESULT_INVALID_EMAIL = -7
        const val X_REQUESTED_WITH = "XMLHttpRequest"
        const val TAG = "PikuDiag"

    }
}

/** 注册表单页的 TK 令牌：`"TK":"..."`，每次页面加载都会轮换 */
internal val REGISTER_TOKEN_REGEX = Regex("""\"TK\":\"([^\"]+)\"""")

internal fun parseRegisterToken(html: String): String? =
    REGISTER_TOKEN_REGEX.find(html)?.groupValues?.get(1)