package com.piku.client.source.poipiku.ui.login

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.source.poipiku.data.auth.PoipikuWebSession
import com.piku.client.source.poipiku.data.remote.ApiConfig
import com.piku.client.source.poipiku.data.repository.AuthRepository
import com.piku.client.core.model.LoginError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PoipikuWebLoginUiState {

    /** 等用户在网页里把登录走完 */
    data object Browsing : PoipikuWebLoginUiState

    /** 网页登录已成立，正在把会话并进应用侧（换 uid、落登录态） */
    data object Adopting : PoipikuWebLoginUiState

    data object Success : PoipikuWebLoginUiState

    data class Failed(val errorRes: Int) : PoipikuWebLoginUiState
}

/**
 * poipiku 网页登录。
 *
 * 与 pixiv 的差异：**没有回调地址可拦**。登录走页面内的 XHR（站点成功后才跳回 `/`），
 * 所以判定只能挂在每轮 onPageFinished 上，去 CookieManager 读一次 cookie 头。
 * 登录页首屏会种一个空的 `POIPIKU_LK`，判据必须**非空**（见 [PoipikuWebSession]），
 * 否则一打开页面就会假装登录成功。
 */
@HiltViewModel
class PoipikuWebLoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val loginPageUrl: String = PoipikuWebSession.LOGIN_PAGE_URL

    private val _uiState = MutableStateFlow<PoipikuWebLoginUiState>(PoipikuWebLoginUiState.Browsing)
    val uiState: StateFlow<PoipikuWebLoginUiState> = _uiState.asStateFlow()

    /**
     * 用户碰过页面没有。
     *
     * WebView 的会话跨登录页存活：上次留下的 `POIPIKU_LK` 会让这一页一打开就被认成已登录，
     * 缺这道闸用户什么都还没做就会被静默接管回上一个账号。登录的每一条路都得先点一下，不会误伤。
     */
    @Volatile
    private var userInteracted: Boolean = false

    /** WebView 报告"用户碰了一下" */
    fun markUserInteraction() {
        userInteracted = true
    }

    /**
     * 重试 = 从头再来：先过期掉 WebView 那份会话并复位闸门。
     * 不清的话站内被拒的 cookie 还在，重载完会立刻再接管一次、又失败，点多少次都一样。
     */
    fun retry() {
        PoipikuWebSession.clearWebViewCookies()
        userInteracted = false
        _uiState.value = PoipikuWebLoginUiState.Browsing
    }

    /**
     * 每次页面加载完成都问一次"成了没"。
     *
     * 只能挂 onPageFinished：登录是页面内的 XHR，过程中没有导航，
     * 站点成功后才 `location.href` 跳一次，那次导航就是唯一的信号点。
     */
    fun onPageSettled() {
        if (_uiState.value !is PoipikuWebLoginUiState.Browsing) return
        if (!userInteracted) return
        val token = PoipikuWebSession.sessionToken(readWebCookieHeader())
        if (token != null) adopt(token)
    }

    /**
     * WebView 那份 cookie 罐与应用侧 OkHttp 的 jar 是两套存储，互不相通，
     * 所以登录信号只能从 WebView 自己这里读。
     */
    private fun readWebCookieHeader(): String? =
        runCatching { CookieManager.getInstance().getCookie(ApiConfig.BASE_URL) }.getOrNull()

    private fun adopt(token: String) {
        if (_uiState.value is PoipikuWebLoginUiState.Adopting) return
        _uiState.value = PoipikuWebLoginUiState.Adopting
        viewModelScope.launch {
            authRepository.adoptWebSession(token)
                .onSuccess { _uiState.value = PoipikuWebLoginUiState.Success }
                .onFailure { _uiState.value = PoipikuWebLoginUiState.Failed(it.toErrorRes()) }
        }
    }

    private fun Throwable.toErrorRes(): Int = when (this) {
        is LoginError.InvalidCredentials -> R.string.poipiku_web_login_error_rejected
        is LoginError.Network -> R.string.login_error_network
        else -> R.string.login_error_unknown
    }

    /**
     * 登录页离场（VM 随导航栈条目销毁）才清 WebView 会话。
     *
     * 不放 Composable 的 onDispose：旋转屏幕时组合会重建、VM 不会，
     * 挂 onDispose 会把正在走的登录连会话一起打断。
     */
    override fun onCleared() {
        PoipikuWebSession.clearWebViewCookies()
    }
}