package com.piku.client.source.ao3.data.auth

import android.util.Log
import com.piku.client.R
import com.piku.client.core.model.AuthStatus
import com.piku.client.core.model.LoginError
import com.piku.client.core.model.WorkSource
import com.piku.client.core.source.SourceAccount
import com.piku.client.core.source.SourceAuth
import com.piku.client.core.source.SourceAuthRoutes
import com.piku.client.source.ao3.data.remote.Ao3Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.net.CookieStore
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * AO3 的登录插件。凭据形态是网页会话：登录成功后 cookie 落在 AO3 自己的罐里，
 * 内容请求带着它就能读到仅注册用户可见的作品；本源不保存密码。
 *
 * 站点的登录与登出都是表单 POST，且会话 cookie 按域隔离——登出只清 archiveofourown.org
 * 那几条，不碰别的源。
 */
@Singleton
class Ao3Auth @Inject constructor(
    private val api: Ao3AuthApi,
    private val store: Ao3AuthStore,
    @Named(Ao3Config.CLIENT_NAME) private val cookieJar: CookieJar,
    @Named(Ao3Config.CLIENT_NAME) private val cookieStore: CookieStore,
) : SourceAuth {

    override val source = WorkSource.AO3

    override val loginRoute: String = SourceAuthRoutes.AO3_LOGIN

    override val logoutMessageRes = R.string.ao3_logout_message

    private val name = store.username()

    private val restored = name != null && hasSessionCookie()

    private val _status = MutableStateFlow(
        if (restored) AuthStatus.LOGGED_IN else AuthStatus.LOGGED_OUT,
    )
    override val status: StateFlow<AuthStatus> = _status.asStateFlow()

    private val _account = MutableStateFlow(name?.takeIf { restored }?.let(::toAccount))
    override val account: StateFlow<SourceAccount?> = _account.asStateFlow()

    init {
        // cookie 在而用户名没了：本地数据被清了一半，按未登录处理，别让假会话顶上去
        if (!restored && hasSessionCookie()) clearCookies()
    }

    /** 账号或密码登录。525/超时是这条链路常见的瞬时失败，整轮重试一次表单（含新 token） */
    suspend fun login(login: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        var error: Throwable = LoginError.Unknown
        repeat(MAX_ATTEMPTS) { attempt ->
            when (val outcome = attemptLogin(login, password)) {
                is Attempt.Ok -> {
                    adopt(outcome.username)
                    return@withContext Result.success(Unit)
                }
                is Attempt.Transient -> {
                    error = outcome.error
                    if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS)
                }
                is Attempt.Rejected -> return@withContext Result.failure(outcome.error)
            }
        }
        Result.failure(error)
    }

    /** 只清本机会话：远端登出要 CSRF 令牌与会话本身，等它回来界面会卡在已登录态 */
    override fun logout() {
        Log.d(TAG, "ao3 logout user=${_account.value?.account}")
        clearCookies()
        store.clear()
        _account.value = null
        _status.value = AuthStatus.LOGGED_OUT
    }

    private suspend fun attemptLogin(login: String, password: String): Attempt {
        val page = try {
            api.loginPage()
        } catch (e: IOException) {
            Log.w(TAG, "ao3 login page failed: ${e.javaClass.simpleName}")
            return Attempt.Transient(LoginError.Network)
        }
        if (!page.isSuccessful) {
            val code = page.code()
            page.errorBody()?.close()
            Log.w(TAG, "ao3 login page HTTP $code")
            return if (code >= 500) Attempt.Transient(LoginError.Network) else Attempt.Rejected(LoginError.Unknown)
        }
        val html = try {
            page.body()?.string().orEmpty()
        } catch (e: IOException) {
            return Attempt.Transient(LoginError.Network)
        }
        val token = Ao3AuthHtml.csrfToken(html)
        if (token == null) {
            Log.w(TAG, "ao3 login page without csrf token, bytes=${html.length}")
            return Attempt.Transient(LoginError.Network)
        }

        val response = try {
            api.login(token, login, password, COMMIT, REMEMBER_ME)
        } catch (e: IOException) {
            Log.w(TAG, "ao3 login submit failed: ${e.javaClass.simpleName}")
            return Attempt.Transient(LoginError.Network)
        }
        val code = response.code()
        val location = response.headers()["Location"]
        response.errorBody()?.close()
        Log.d(TAG, "ao3 login HTTP $code location=${location?.substringBefore('?')}")

        return when (val outcome = classifyLogin(code, location)) {
            is Ao3LoginOutcome.Success -> Attempt.Ok(outcome.username)
            Ao3LoginOutcome.InvalidCredentials -> Attempt.Rejected(LoginError.InvalidCredentials)
            // 52x 是 Cloudflare 到源站握手中断，表单还没被处理，重试安全
            Ao3LoginOutcome.Rejected -> if (code >= 500) {
                Attempt.Transient(LoginError.Network)
            } else {
                Attempt.Rejected(LoginError.Unknown)
            }
        }
    }

    private fun adopt(username: String) {
        store.save(username)
        _account.value = toAccount(username)
        _status.value = AuthStatus.LOGGED_IN
        Log.d(TAG, "ao3 login ok user=$username")
    }

    private fun hasSessionCookie(): Boolean = runCatching {
        cookieJar.loadForRequest(Ao3Config.BASE_URL.toHttpUrl())
            .any { it.name == SESSION_COOKIE && it.value.isNotBlank() }
    }.getOrDefault(false)

    private fun clearCookies() {
        cookieStore.getCookies()
            .filter { it.domain?.removePrefix(".") == Ao3Config.MAIN_HOST }
            .forEach { cookieStore.remove(null, it) }
    }

    private fun toAccount(username: String): SourceAccount = SourceAccount(
        displayName = username,
        account = username,
    )

    private sealed interface Attempt {
        data class Ok(val username: String) : Attempt
        data class Rejected(val error: Throwable) : Attempt
        data class Transient(val error: Throwable) : Attempt
    }

    private companion object {
        const val TAG = "PikuDiag"

        /** 成功登录后站点下发的"已登录"标记，登出时会被清掉 */
        const val SESSION_COOKIE = "user_credentials"

        const val COMMIT = "Log In"
        const val REMEMBER_ME = "1"

        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 700L
    }
}
