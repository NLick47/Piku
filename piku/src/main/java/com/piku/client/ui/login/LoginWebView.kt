package com.piku.client.ui.login

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.SystemClock
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.piku.client.ui.common.PikuBackButton
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.PikuColors
import kotlinx.coroutines.delay

internal fun loginWebViewClient(
    onNavigate: (String) -> Boolean,
    onStarted: () -> Unit,
    onSettled: (String) -> Unit,
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

    override fun onPageCommitVisible(view: WebView, url: String?) = onSettled(COMMIT_VISIBLE)

    override fun onPageFinished(view: WebView, url: String?) = onSettled(FINISHED)

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        // 错误码与地址都要打：只有"失败了"这一句，真机上分不出是 DNS、连接被重置还是证书问题
        Log.d(TAG, "resource failed: code=${error.errorCode} '${error.description}' url=${request.url}")
        if (request.isForMainFrame) onMainFrameFailed()
    }
}

private const val COMMIT_VISIBLE = "commitVisible"
private const val FINISHED = "finished"

internal class WebViewHandle {
    var view: WebView? = null
    var loadedUrl: String? = null
}

@Composable
internal fun LoginOverlay(dark: Boolean, content: @Composable () -> Unit) {
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

internal fun WebView.applyLoginWebSettings(userAgent: String = LOGIN_WEB_VIEW_USER_AGENT) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.userAgentString = userAgent
    setBackgroundColor(AndroidColor.WHITE)
}

internal const val LOGIN_WEB_VIEW_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"

internal enum class WebPageState { LOADING, READY, FAILED }

private const val PAGE_LOAD_WATCHDOG_MS = 15_000L

private const val TAG = "PikuDiag"

@Composable
internal fun LoginWebScreen(
    title: String,
    url: String,
    dark: Boolean,
    onBack: () -> Unit,
    onNavigate: (String) -> Boolean,
    modifier: Modifier = Modifier,
    onPageSettled: () -> Unit = {},
    onRetry: () -> Unit = {},
    onUserInteraction: () -> Unit = {},
    overlay: @Composable BoxScope.(state: WebPageState, retryPage: () -> Unit) -> Unit,
) {
    var pageState by remember { mutableStateOf(WebPageState.LOADING) }

    val web = remember { WebViewHandle() }
    var loadEpoch by remember { mutableIntStateOf(0) }

    var settledEpoch by remember { mutableIntStateOf(-1) }

    var loadStartedAt by remember { mutableLongStateOf(0L) }

    fun settle(reason: String) {
        if (settledEpoch == loadEpoch) return
        if (pageState == WebPageState.FAILED) return
        settledEpoch = loadEpoch
        Log.d(
            TAG,
            "login page usable via $reason after ${SystemClock.elapsedRealtime() - loadStartedAt}ms" +
                ", url=${web.view?.url}",
        )
        pageState = WebPageState.READY
        onPageSettled()
    }

    LaunchedEffect(loadEpoch) {
        delay(PAGE_LOAD_WATCHDOG_MS)
        if (pageState != WebPageState.LOADING) return@LaunchedEffect
        val drewContent = (web.view?.contentHeight ?: 0) > 0
        Log.d(TAG, "login page watchdog fired after ${PAGE_LOAD_WATCHDOG_MS}ms, drewContent=$drewContent, url=$url")
        if (drewContent) settle("watchdog+content") else pageState = WebPageState.FAILED
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
        modifier = modifier
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
                    text = title,
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
                        val createdAt = SystemClock.elapsedRealtime()
                        WebView(context).apply {
                            web.view = this
                            applyLoginWebSettings()
                            // 只记"碰过"，不消费事件：拦住事件会让整个页面点不动
                            setOnTouchListener { _, _ ->
                                onUserInteraction()
                                false
                            }
                            webViewClient = loginWebViewClient(
                                onNavigate = onNavigate,
                                onStarted = {
                                    // elapsedRealtime 单调，跨设备可比；墙钟会被改
                                    loadStartedAt = SystemClock.elapsedRealtime()
                                    loadEpoch++
                                    pageState = WebPageState.LOADING
                                },
                                onSettled = { reason -> settle(reason) },
                                onMainFrameFailed = {
                                    Log.d(
                                        TAG,
                                        "login page main frame failed after " +
                                            "${SystemClock.elapsedRealtime() - loadStartedAt}ms, url=$url",
                                    )
                                    pageState = WebPageState.FAILED
                                },
                            )
                            // WebView 首次创建要初始化内核，这段开销在低端机上不小，
                            // 单列出来，别混进"页面加载慢"里
                            Log.d(
                                TAG,
                                "login webview created in ${SystemClock.elapsedRealtime() - createdAt}ms",
                            )
                        }
                    },
                    update = { view ->
                        // url 还没准备好（pixiv 生成挑战前是空串）就别动 WebView
                        if (url.isNotBlank() && web.loadedUrl != url) {
                            web.loadedUrl = url
                            view.loadUrl(url)
                        }
                    },
                )

                if (pageState == WebPageState.LOADING) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
                        color = PikuColors.accent,
                        strokeWidth = 2.dp,
                    )
                }

                overlay(pageState) {
                    pageState = WebPageState.LOADING
                    onRetry()
                    // reload 覆盖"地址没变"（poipiku）；地址变了（pixiv 换 PKCE 挑战）
                    // 则由上面的 update 块按新地址加载
                    web.view?.reload()
                }
            }
        }
    }
}
