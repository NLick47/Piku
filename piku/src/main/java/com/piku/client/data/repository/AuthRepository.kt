package com.piku.client.data.repository

import android.os.SystemClock
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
import com.piku.client.domain.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.CookieStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val cookieJar: CookieJar,
    private val cookieStore: CookieStore,
    private val sessionMonitor: SessionMonitor,
    private val credentialStore: CredentialStore,
) {

    private val _authStatus =
        MutableStateFlow(if (hasSession()) AuthStatus.LOGGED_IN else AuthStatus.LOGGED_OUT)
    val authStatus: StateFlow<AuthStatus> = _authStatus.asStateFlow()

    /** 自动重登成功但登录态未变化时的事件，供页面感知"会话已更新"并刷新数据 */
    private val _sessionRefreshed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionRefreshed: SharedFlow<Unit> = _sessionRefreshed.asSharedFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private var uid: Long? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 自动重登互斥与防抖状态 */
    private val reloginLock = Mutex()
    private var lastReloginAt = 0L
    private var reloginFailures = 0

    init {
        // 冷启动恢复：已有会话 cookie 但 uid 尚未赋值时，从持久化恢复，
        // 否则 refreshUserProfile 会因 uid 为 null 而跳过（头像/昵称都拿不到）
        if (hasSession()) {
            uid = credentialStore.loadUid()
            Log.d(TAG, "cold start: restored uid=$uid")
        }
        scope.launch {
            sessionMonitor.sessionCleared.collect {
                reloginLock.withLock { autoReLogin() }
            }
        }
    }

    fun isLoggedIn(): Boolean = _authStatus.value == AuthStatus.LOGGED_IN

    fun currentUserId(): Long? = uid

    suspend fun login(email: String, password: String): Result<Unit> {
        val response = apiCall { authApi.login(email, password) }
        return response.fold(
            onSuccess = { login ->
                Log.d(TAG, "login response: result=${login.result}")
                when {
                    login.result == RESULT_LOCKED -> Result.failure(LoginError.Locked)
                    login.result < 0 -> Result.failure(LoginError.InvalidCredentials)
                    else -> {
                        uid = login.result.toLong()
                        _authStatus.value = AuthStatus.LOGGED_IN
                        credentialStore.save(email, password)
                        credentialStore.saveUid(login.result.toLong())
                        Log.d(TAG, "login ok, uid=${login.result} session=${hasSession()}")
                        refreshUserProfile()
                        Result.success(Unit)
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

    suspend fun refreshUserProfile() {
        if (_authStatus.value != AuthStatus.LOGGED_IN) {
            Log.d(TAG, "refreshUserProfile: not logged in, skip")
            _userProfile.value = null
            return
        }
        val myUid = uid
        if (myUid == null) {
            Log.d(TAG, "refreshUserProfile: uid null, skip")
            return
        }
        val profile = runCatching {
            val settingResponse = authApi.getMyEditSetting(myUid)
            val settingHtml = settingResponse.body()?.string() ?: ""
            val preview = Regex("""PreviewImg" src="([^"]+)""")
                .find(settingHtml)?.groupValues?.get(1)
            Log.d(
                TAG,
                "getMyEditSetting: uid=$myUid code=${settingResponse.code()} len=${settingHtml.length} " +
                    "previewRaw=$preview",
            )
            val avatarUrl = preview?.let { url ->
                if (Regex("""_\d+\.(jpg|jpeg|png)$""").containsMatchIn(url)) {
                    url
                } else {
                    url + "_120.jpg"
                }
            }
            val profileUrl = Regex("""href="(https?://[^"]*?poipiku\.com/$myUid/)""")
                .find(settingHtml)?.groupValues?.get(1)
                ?: "https://poipiku.com/$myUid/"
            UserProfile(
                uid = myUid.toString(),
                avatarUrl = avatarUrl,
                profileUrl = profileUrl,
                name = fetchDisplayName(myUid),
            )
        }.getOrNull()
        Log.d(TAG, "profile=$profile")
        _userProfile.value = profile
    }

    /**
     * 从公开用户主页解析昵称，按优先级：
     * 1. 第一个 `<h2 class="IllustUserName">`（即页主）
     * 2. `<title>` 中的 `{昵称}のポイピク | イラストとか箱「ポイピク」`
     * 3. 第一个头像 `<img class="IllustUserThumb" ... alt="昵称">`
     */
    private suspend fun fetchDisplayName(uid: Long): String? = runCatching {
        val response = authApi.getUserTop(uid)
        val html = response.body()?.string()
        Log.d(TAG, "getUserTop: uid=$uid code=${response.code()} len=${html?.length ?: -1}")
        if (html.isNullOrEmpty()) return@runCatching null
        val fromH2 = Regex("""<h2 class="IllustUserName">([^<]+)</h2>""")
            .find(html)?.groupValues?.get(1)
        val fromTitle = Regex("""<title>([^<]+)のポイピク \| イラストとか箱「ポイピク」</title>""")
            .find(html)?.groupValues?.get(1)
        val fromAlt = Regex("""<img class="IllustUserThumb"[^>]*alt="([^"]+)"""")
            .find(html)?.groupValues?.get(1)
        val raw = fromH2 ?: fromTitle ?: fromAlt
        Log.d(TAG, "fetchDisplayName: uid=$uid h2=$fromH2 title=$fromTitle alt=$fromAlt")
        raw
            ?.let { decodeHtmlEntities(it) }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun decodeHtmlEntities(input: String): String = input
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("&#(\\d+);")) { m ->
            m.groupValues[1].toIntOrNull()?.let { it.toChar().toString() } ?: m.value
        }

    fun logout() {
        Log.d(TAG, "logout")
        clearSession()
    }

    /**
     * 修改昵称（网页端 MyEditSettingPcV 的 UpdateNickName 同款）。
     * 成功（result>0）后立即更新本地 profile，无需整页刷新。
     */
    suspend fun updateNickName(name: String): Result<Unit> {
        val myUid = uid
        if (myUid == null) {
            Log.d(TAG, "updateNickName: uid null, skip")
            return Result.failure(AppError.Unknown)
        }
        val response = apiCall { authApi.updateNickName(myUid, name) }
            .getOrElse { return Result.failure(it) }
        Log.d(TAG, "updateNickName: result=${response.result}")
        return if (response.result > 0) {
            _userProfile.value = _userProfile.value?.copy(name = name)
            Result.success(Unit)
        } else {
            Result.failure(UpdateRejected(response.result))
        }
    }

    /**
     * 上传头像（网页端 updateFile("/f/UpdateProfileFileF.jsp", ...) 同款：
     * form 提交 UID + DATA(base64)，result==0 表示成功）。
     * 成功后头像 URL 会变化，重新解析设置页刷新 profile。
     */
    suspend fun updateAvatar(imageFile: java.io.File): Result<Unit> {
        val myUid = uid
        if (myUid == null) {
            Log.d(TAG, "updateAvatar: uid null, skip")
            return Result.failure(AppError.Unknown)
        }
        val dataBase64 = runCatching {
            java.util.Base64.getEncoder().encodeToString(imageFile.readBytes())
        }.getOrElse {
            Log.d(TAG, "updateAvatar: read/encode failed", it)
            return Result.failure(AppError.Unknown)
        }
        if (dataBase64.length > MAX_AVATAR_BASE64_LEN) {
            Log.d(TAG, "updateAvatar: too large b64Len=${dataBase64.length}")
            return Result.failure(AvatarTooLarge)
        }
        val response = apiCall { authApi.updateProfileFile(myUid, dataBase64) }
            .getOrElse { return Result.failure(it) }
        Log.d(TAG, "updateAvatar: result=${response.result}")
        return if (response.result == 0) {
            refreshUserProfile()
            Result.success(Unit)
        } else {
            Result.failure(UpdateRejected(response.result))
        }
    }

    /**
     * session 失效后的自动重登：
     * - 无保存凭据 → 直接登出
     * - 距上次尝试过近 → 跳过（防抖，避免错误响应反复触发）
     * - 登录成功 → 恢复登录态（页面观察 authStatus 自动刷新）
     * - 连续失败超限 → 清除凭据并登出（避免死循环/被限频）
     */
    private suspend fun autoReLogin() {
        if (_authStatus.value != AuthStatus.LOGGED_IN) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastReloginAt < RELOGIN_MIN_INTERVAL_MS) return
        lastReloginAt = now
        val credentials = credentialStore.load()
        if (credentials == null) {
            Log.d(TAG, "auto re-login: no saved credentials")
            clearSession()
            return
        }
        login(credentials.email, credentials.password)
            .onSuccess {
                reloginFailures = 0
                Log.d(TAG, "auto re-login ok")
                _sessionRefreshed.tryEmit(Unit)
            }
            .onFailure { error ->
                reloginFailures++
                Log.d(TAG, "auto re-login failed: $error failures=$reloginFailures")
                if (reloginFailures >= MAX_RELOGIN_FAILURES) {
                    clearSession()
                }
            }
    }

    /** 清除 cookie、凭据与登录态 */
    private fun clearSession() {
        cookieStore.removeAll()
        credentialStore.clear()
        uid = null
        _userProfile.value = null
        _authStatus.value = AuthStatus.LOGGED_OUT
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
        const val RELOGIN_MIN_INTERVAL_MS = 30_000L
        const val MAX_RELOGIN_FAILURES = 3
        const val TAG = "PikuDiag"
        /**
         * 网页端 updateFile 的客户端限制：base64 长度 <= limitMiByte(1.0) * 1e6 * 1.3。
         * 留一点余量防止服务端 -1。
         */
        const val MAX_AVATAR_BASE64_LEN = 1_250_000
    }

    /** 服务端返回 result<=0 时抛出，携带原始码供上层提示 */
    class UpdateRejected(val code: Int) : Exception("update rejected: $code")

    /** 头像超出网页端 1MB 限制 */
    data object AvatarTooLarge : Exception("avatar too large")
}

/** 注册表单页的 TK 令牌：`"TK":"..."`，每次页面加载都会轮换 */
internal val REGISTER_TOKEN_REGEX = Regex("""\"TK\":\"([^\"]+)\"""")

internal fun parseRegisterToken(html: String): String? =
    REGISTER_TOKEN_REGEX.find(html)?.groupValues?.get(1)