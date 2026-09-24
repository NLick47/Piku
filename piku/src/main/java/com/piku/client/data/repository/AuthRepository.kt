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
import com.piku.client.domain.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
     * 自动重登成功且登录态未变化时的事件，供页面感知"会话已更新"并刷新数据。
     * 登录态真的变了时不发：页面已经在 authStatus 上重载过了，两个都发会重载两遍
     */
    private val _sessionRefreshed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionRefreshed: SharedFlow<Unit> = _sessionRefreshed.asSharedFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

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
            _userProfile.value = credentialStore.loadProfile()
            Log.d(TAG, "cold start: restored uid=$uid profile=${_userProfile.value != null}")
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
                            Log.d(TAG, "login ok, uid=${login.result} session=${hasSession()}")
                            refreshUserProfile()
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
            val preview = PREVIEW_IMG_REGEX
                .find(settingHtml)?.groupValues?.get(1)
            Log.d(
                TAG,
                "getMyEditSetting: uid=$myUid code=${settingResponse.code()} len=${settingHtml.length} " +
                    "previewRaw=$preview",
            )
            val avatarUrl = preview?.let { url ->
                if (AVATAR_SUFFIX_REGEX.containsMatchIn(url)) {
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
        // 失败保留旧缓存；校验会话与 uid，防登出/换号后在途请求写回
        if (profile != null && _authStatus.value == AuthStatus.LOGGED_IN && uid == myUid) {
            _userProfile.value = profile
            credentialStore.saveProfile(profile)
        }
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
        val fromH2 = H2_NAME_REGEX
            .find(html)?.groupValues?.get(1)
        val fromTitle = TITLE_NAME_REGEX
            .find(html)?.groupValues?.get(1)
        val fromAlt = ALT_NAME_REGEX
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
        .replace(HTML_ENTITY_DECIMAL_REGEX) { m ->
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
            _userProfile.value?.let { credentialStore.saveProfile(it) }
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
     * 会话失效（或冷启动没拿到有效会话）后的自动重登：
     * - 无保存凭据 → 自认为登录中则登出，否则什么都不做
     * - 在防抖/退避窗口内 → 跳过，避免错误响应反复触发
     * - 凭据失效或账号锁定连续多次 → 清除凭据并登出
     * - 网络等瞬态失败 → 只拉长退避，不淘汰会话（弱网抖动不该删掉用户密码）
     */
    private suspend fun autoReLogin() = sessionMutex.withLock<Unit> {
        // 登录态变了会由 authStatus 驱动页面重载，这里再发一次会重载两遍
        val wasLoggedIn = isLoggedIn()
        if (!reloginPolicy.allowAttempt(runtime.now())) return@withLock
        val credentials = credentialStore.load()
        if (credentials == null) {
            Log.d(TAG, "auto re-login: no saved credentials")
            if (isLoggedIn()) clearSession()
            return@withLock
        }
        loginLocked(credentials.email, credentials.password)
            .onSuccess {
                Log.d(TAG, "auto re-login ok")
                if (wasLoggedIn) _sessionRefreshed.tryEmit(Unit)
            }
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
        sessionEpoch.incrementAndGet()
        cookieStore.removeAll()
        credentialStore.clear()
        uid = null
        _userProfile.value = null
        _authStatus.value = AuthStatus.LOGGED_OUT
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
        /**
         * 网页端 updateFile 的客户端限制：base64 长度 <= limitMiByte(1.0) * 1e6 * 1.3。
         * 留一点余量防止服务端 -1。
         */
        const val MAX_AVATAR_BASE64_LEN = 1_250_000

        private val PREVIEW_IMG_REGEX = Regex("""PreviewImg" src="([^"]+)""")
        private val AVATAR_SUFFIX_REGEX = Regex("""_\d+\.(jpg|jpeg|png)$""")
        private val H2_NAME_REGEX = Regex("""<h2 class="IllustUserName">([^<]+)</h2>""")
        private val TITLE_NAME_REGEX = Regex("""<title>([^<]+)のポイピク \| イラストとか箱「ポイピク」</title>""")
        private val ALT_NAME_REGEX = Regex("""<img class="IllustUserThumb"[^>]*alt="([^"]+)"""")
        private val HTML_ENTITY_DECIMAL_REGEX = Regex("&#(\\d+);")
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