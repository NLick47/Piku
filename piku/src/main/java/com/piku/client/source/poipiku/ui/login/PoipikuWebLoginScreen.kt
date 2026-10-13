package com.piku.client.source.poipiku.ui.login

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piku.client.R
import com.piku.client.core.ui.theme.LocalDarkTheme
import com.piku.client.core.ui.theme.PikuColors

/**
 * poipiku 网页登录：站内登录页（邮箱密码 / X·Twitter / Facebook / 邮箱验证码）装进 WebView，
 * 登录成立后会话 cookie 由 [PoipikuWebLoginViewModel] 并进应用侧。
 *
 * 外壳（WebView、加载状态、看门狗、失败重试）走共用件 [LoginWebScreen]，
 * 这里只负责 poipiku 特有的一段：登录过程不发生导航，只能在每轮加载后查 cookie。
 */
@Composable
fun PoipikuWebLoginScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
) {
    val viewModel: PoipikuWebLoginViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dark = LocalDarkTheme.current

    LaunchedEffect(uiState) {
        if (uiState is PoipikuWebLoginUiState.Success) onSuccess()
    }

    LoginWebScreen(
        title = stringResource(R.string.poipiku_web_login_title),
        url = viewModel.loginPageUrl,
        dark = dark,
        onBack = onBack,
        // 没有回调地址可拦：站内 XHR 登录成功不发生导航，一律放行给 WebView
        onNavigate = { false },
        onPageSettled = viewModel::onPageSettled,
        onRetry = viewModel::retry,
        onUserInteraction = viewModel::markUserInteraction,
    ) { pageState, retryPage ->
        val state = uiState
        when {
            state is PoipikuWebLoginUiState.Adopting -> LoginOverlay(dark = dark) {
                CircularProgressIndicator(color = PikuColors.accent, strokeWidth = 2.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.poipiku_web_login_adopting),
                    color = PikuColors.textSecondary,
                    fontSize = 13.sp,
                )
            }

            state is PoipikuWebLoginUiState.Failed -> LoginOverlay(dark = dark) {
                LoginErrorBanner(stringResource(state.errorRes), dark)
                Spacer(Modifier.height(18.dp))
                LoginGlassButton(
                    text = stringResource(R.string.common_retry),
                    enabled = true,
                    loading = false,
                    onClick = retryPage,
                    dark = dark,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            pageState == WebPageState.FAILED -> LoginOverlay(dark = dark) {
                Text(
                    text = stringResource(R.string.poipiku_web_login_page_failed),
                    color = PikuColors.textSecondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(18.dp))
                LoginGlassButton(
                    text = stringResource(R.string.common_retry),
                    enabled = true,
                    loading = false,
                    // 只是网页没打开：原样重载即可，没有"授权码作废"那类需要重置的东西
                    onClick = retryPage,
                    dark = dark,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}