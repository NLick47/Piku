package com.piku.client.ui.login

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.piku.client.R
import com.piku.client.ui.common.PikuBackButton
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.PikuColors

@Composable
fun PixivLoginScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
) {
    val viewModel: PixivLoginViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val url by viewModel.url.collectAsStateWithLifecycle()
    val dark = LocalDarkTheme.current

    var pageLoading by remember { mutableStateOf(true) }
    var pageFailed by remember { mutableStateOf(false) }
    // WebView 引用与已加载地址走普通容器：它们只是给回调留的把手，
    // 没有一处要在组合期读，塞进 Compose 快照只会平白多几次失效
    val web = remember { WebViewHandle() }

    LaunchedEffect(uiState) {
        if (uiState is PixivLoginUiState.Success) onSuccess()
    }

    LaunchedEffect(pageLoading) {
        if (!pageLoading) return@LaunchedEffect
        delay(PAGE_LOAD_TIMEOUT_MS)
        if (uiState is PixivLoginUiState.Browsing) {
            web.view?.stopLoading()
            pageLoading = false
            pageFailed = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            web.view?.run {
                stopLoading()
                destroy()
            }
            web.view = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (dark) listOf(HomeBgTopDark, HomeBgBottomDark)
                    else listOf(HomeBgTopLight, HomeBgBottomLight),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PikuBackButton(onClick = onBack, dark = dark, glass = true)
                Spacer(Modifier.padding(horizontal = 6.dp))
                Text(
                    text = stringResource(R.string.pixiv_login_title),
                    color = PikuColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(0.5.dp, PikuColors.border, RoundedCornerShape(18.dp))
                    .background(Color.White),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            web.view = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = PIXIV_WEB_VIEW_USER_AGENT
                            setBackgroundColor(AndroidColor.WHITE)
                            webViewClient = pixivWebViewClient(
                                onNavigate = viewModel::onNavigate,
                                onStarted = {
                                    pageLoading = true
                                    pageFailed = false
                                },
                                onFinished = { pageLoading = false },
                                onMainFrameFailed = {
                                    pageLoading = false
                                    pageFailed = true
                                },
                            )
                        }
                    },
                    update = { view ->
                        if (url.isNotBlank() && web.loadedUrl != url) {
                            web.loadedUrl = url
                            view.loadUrl(url)
                        }
                    },
                )

                if (pageLoading && uiState is PixivLoginUiState.Browsing) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
                        color = PikuColors.accent,
                        strokeWidth = 2.dp,
                    )
                }

                when (val state = uiState) {
                    is PixivLoginUiState.Exchanging -> LoginOverlay(dark = dark) {
                        CircularProgressIndicator(color = PikuColors.accent, strokeWidth = 2.dp)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.pixiv_login_exchanging),
                            color = PikuColors.textSecondary,
                            fontSize = 13.sp,
                        )
                    }

                    is PixivLoginUiState.Failed -> LoginOverlay(dark = dark) {
                        LoginErrorBanner(stringResource(state.errorRes), dark)
                        Spacer(Modifier.height(18.dp))
                        LoginGlassButton(
                            text = stringResource(R.string.common_retry),
                            enabled = true,
                            loading = false,
                            onClick = {
                                pageLoading = true
                                pageFailed = false
                                viewModel.retry()
                            },
                            dark = dark,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    else -> if (pageFailed) {
                        LoginOverlay(dark = dark) {
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
                                // 只是网页没打开：原样重载，挑战还是同一条，不必重新授权
                                onClick = {
                                    pageFailed = false
                                    pageLoading = true
                                    web.view?.reload()
                                },
                                dark = dark,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 只在旁边看网页跳到哪：
 * - 回调地址（带授权码）由 [onNavigate] 消费，页面不再加载；
 * - 其余 http(s) 交给 WebView 自己走，cookie/表单/POST 都由它保持连续；
 * - 别的自定义 scheme 一律拦下，我们没有能处理它们的页面。
 */
private fun pixivWebViewClient(
    onNavigate: (String) -> Boolean,
    onStarted: () -> Unit,
    onFinished: () -> Unit,
    onMainFrameFailed: () -> Unit,
) = object : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val target = request.url.toString()
        if (onNavigate(target)) return true
        return !target.startsWith("http://") && !target.startsWith("https://")
    }

    @Deprecated("老 API 只在旧机型上被调用，行为与上面那条一致")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        if (onNavigate(url)) return true
        return !url.startsWith("http://") && !url.startsWith("https://")
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = onStarted()

    override fun onPageFinished(view: WebView, url: String?) = onFinished()

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        // 只有主框架失败才是"登录页打不开"：子资源（头像、埋点）失败不该把整页判死
        if (request.isForMainFrame) onMainFrameFailed()
    }
}

@Composable
private fun LoginOverlay(dark: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = if (dark) 0.92f else 0.96f))
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            content()
        }
    }
}

/** WebView 的把手：引用 + 已经加载过的地址。刻意不进 Compose 快照（组合期没人读它） */
private class WebViewHandle {
    var view: WebView? = null
    var loadedUrl: String? = null
}

/** 网页端登录页对 UA 敏感，与接口侧统一用桌面 Chrome 串 */
private const val PIXIV_WEB_VIEW_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"

private const val PAGE_LOAD_TIMEOUT_MS = 4_000L
