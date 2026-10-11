package com.piku.client.ui.login

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.PikuColors

/**
 * pixiv 网页登录：在官方登录页上走 PKCE 授权，授权码换令牌后登录态交给令牌。
 *
 * 外壳（WebView、加载状态、看门狗、失败重试）走共用件 [LoginWebScreen]，
 * 这里只负责 pixiv 特有的那一段：授权码的拦截与换令牌。
 */
@Composable
fun PixivLoginScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
) {
    val viewModel: PixivLoginViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val url by viewModel.url.collectAsStateWithLifecycle()
    val dark = LocalDarkTheme.current

    LaunchedEffect(uiState) {
        if (uiState is PixivLoginUiState.Success) onSuccess()
    }

    LoginWebScreen(
        title = stringResource(R.string.pixiv_login_title),
        url = url,
        dark = dark,
        onBack = onBack,
        // 回调地址（带授权码）由这里消费，页面不再加载；其余导航原样放行
        onNavigate = viewModel::onNavigate,
        // pixiv 不需要：登录信号来自授权码回调，不是查 cookie
        onRetry = viewModel::retry,
    ) { pageState, retryPage ->
        val state = uiState
        when {
            state is PixivLoginUiState.Exchanging -> LoginOverlay(dark = dark) {
                CircularProgressIndicator(color = PikuColors.accent, strokeWidth = 2.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.pixiv_login_exchanging),
                    color = PikuColors.textSecondary,
                    fontSize = 13.sp,
                )
            }

            state is PixivLoginUiState.Failed -> LoginOverlay(dark = dark) {
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
                    text = stringResource(R.string.pixiv_login_page_failed),
                    color = PikuColors.textSecondary,
                    fontSize = 13.sp,
                )
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
        }
    }
}
