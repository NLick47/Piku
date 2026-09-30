package com.piku.client.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.data.auth.PixivAuthError
import com.piku.client.data.auth.PixivAuthRepository
import com.piku.client.data.auth.PixivPkce
import com.piku.client.data.auth.parsePixivAuthCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PixivLoginUiState {

    /** 等用户在网页里把登录走完 */
    data object Browsing : PixivLoginUiState

    /** 拿到授权码，正在换令牌 */
    data object Exchanging : PixivLoginUiState

    data object Success : PixivLoginUiState

    data class Failed(val errorRes: Int) : PixivLoginUiState
}


@HiltViewModel
class PixivLoginViewModel @Inject constructor(
    private val repository: PixivAuthRepository,
) : ViewModel() {

    var verifier by mutableStateOf(PixivPkce.newVerifier())
        private set

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _uiState = MutableStateFlow<PixivLoginUiState>(PixivLoginUiState.Browsing)
    val uiState: StateFlow<PixivLoginUiState> = _uiState.asStateFlow()

    init {
        loadLoginPage()
    }

    /** 出错后重试：换一条新挑战，旧授权码已经作废 */
    fun retry() {
        _uiState.value = PixivLoginUiState.Browsing
        verifier = PixivPkce.newVerifier()
        loadLoginPage()
    }

    /**
     * WebView 每次导航都过这里。返回 true = 这条地址由我们消费掉：
     * 回调地址里带授权码，取出即换令牌，不需要再按地址加载任何东西。
     */
    fun onNavigate(url: String): Boolean {
        val code = parsePixivAuthCode(url) ?: return false
        exchange(code)
        return true
    }

    private fun loadLoginPage() {
        _url.value = repository.loginPageUrl(PixivPkce.challenge(verifier))
    }

    private fun exchange(code: String) {
        if (_uiState.value is PixivLoginUiState.Exchanging) return
        _uiState.value = PixivLoginUiState.Exchanging
        viewModelScope.launch {
            repository.completeLogin(code, verifier)
                .onSuccess { _uiState.value = PixivLoginUiState.Success }
                .onFailure { _uiState.value = PixivLoginUiState.Failed(it.toErrorRes()) }
        }
    }

    /**
     * 登录页真的离场了（VM 随导航栈条目销毁）才收尾：清掉 WebView 留下的 pixiv 网页会话。
     *
     * 放在这里而不是 Composable 的 onDispose：旋转屏幕时组合会重建、但 VM 不会，
     * 挂 onDispose 会把正在走的授权流程连会话一起打断，用户得从头登一遍。
     * 令牌本身在 [repository] 里，与这份网页 cookie 无关，清掉不影响已完成的登录。
     */
    override fun onCleared() {
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
    }

    private fun Throwable.toErrorRes(): Int = when (this) {
        is PixivAuthError.Network -> R.string.pixiv_login_error_network
        is PixivAuthError.CredentialRejected -> R.string.pixiv_login_error_rejected
        is PixivAuthError.Blocked -> R.string.pixiv_login_error_blocked
        is PixivAuthError.RateLimited -> R.string.pixiv_login_error_ratelimited
        else -> R.string.pixiv_login_error_unknown
    }
}
