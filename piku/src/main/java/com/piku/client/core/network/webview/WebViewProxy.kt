package com.piku.client.core.network.webview

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import kotlin.coroutines.resume

/**
 * 登录 WebView 的进程级代理。
 *
 * hakimi 的 VPN 模式只是给 VPN 网络下发 HTTP 代理；WebView 会不会读取它取决于
 * WebView 内核和宿主 App。这里用 AndroidX WebKit 的 ProxyController 显式指定，
 * 不再依赖系统/VPN 的代理下发。
 *
 * 代理规则是「hakimi 优先，失败直连」：ProxyConfig.addDirect() 会在代理连接
 * 失败时回退 DIRECT，所以 hakimi 没启动时登录页仍然能打开。
 *
 * 注意：ProxyController 是进程级设置，会作用于本 App 的所有 WebView。Piku 目前
 * 只有登录页使用 WebView，因此可以接受。
 */
object WebViewProxy {
    private const val TAG = "PikuDiag"
    private const val DEFAULT_PORT = 8443
    private const val HEALTH_TIMEOUT_MS = 600

    @Volatile
    var isApplied: Boolean = false
        private set

    /**
     * 探测 hakimi 本地代理并在可用时安装代理覆盖。
     *
     * @return true 表示已安装代理覆盖；false 表示本次应直接加载（WebView 会使用
     * 系统代理，或者在没有系统代理时直连）。
     */
    suspend fun applyIfAvailable(context: Context): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            Log.d(TAG, "webview proxy override unsupported")
            return false
        }

        val port = withContext(Dispatchers.IO) { findHakimiPort(context) }
        if (port == null) {
            Log.d(TAG, "hakimi proxy not available")
            clear(context)
            return false
        }

        val config = ProxyConfig.Builder()
            .addProxyRule("127.0.0.1:$port")
            .addDirect()
            .build()
        return try {
            suspendCancellableCoroutine<Boolean> { continuation ->
                ProxyController.getInstance().setProxyOverride(
                    config,
                    ContextCompat.getMainExecutor(context),
                ) {
                    isApplied = true
                    Log.d(TAG, "webview proxy override applied: 127.0.0.1:$port")
                    if (continuation.isActive) continuation.resume(true)
                }
            }
        } catch (error: Throwable) {
            Log.w(TAG, "applying webview proxy override failed", error)
            false
        }
    }

    /** 清除代理覆盖，让 WebView 回到系统代理/直连。 */
    suspend fun clear(context: Context) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            isApplied = false
            return
        }
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                ProxyController.getInstance().clearProxyOverride(
                    ContextCompat.getMainExecutor(context),
                ) {
                    isApplied = false
                    Log.d(TAG, "webview proxy override cleared")
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        } catch (error: Throwable) {
            isApplied = false
            Log.w(TAG, "clearing webview proxy override failed", error)
        }
    }

    /**
     * 优先使用 hakimi VPN 下发的实际端口；没有 VPN 时退回默认的 8443（本地代理
     * 模式）。找到端口后还要过 /hakimi/health，避免把登录页交给一个只占着端口的
     * 死核心。
     */
    private fun findHakimiPort(context: Context): Int? {
        val candidates = buildList {
            activeVpnProxyPort(context)?.let(::add)
            add(DEFAULT_PORT)
        }.distinct()
        return candidates.firstOrNull(::isHakimiHealthy)
    }

    private fun activeVpnProxyPort(context: Context): Int? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return manager.allNetworks.firstNotNullOfOrNull { network ->
            val capabilities = manager.getNetworkCapabilities(network)
                ?: return@firstNotNullOfOrNull null
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return@firstNotNullOfOrNull null
            }
            val proxy = manager.getLinkProperties(network)?.httpProxy
                ?: return@firstNotNullOfOrNull null
            val host = proxy.host ?: return@firstNotNullOfOrNull null
            if (host == "127.0.0.1" || host == "localhost" || host == "::1") {
                proxy.port
            } else {
                null
            }
        }
    }

    private fun isHakimiHealthy(port: Int): Boolean {
        val connection = runCatching {
            URL("http://127.0.0.1:$port/hakimi/health")
                .openConnection(Proxy.NO_PROXY) as HttpURLConnection
        }.getOrNull() ?: return false
        return try {
            connection.connectTimeout = HEALTH_TIMEOUT_MS
            connection.readTimeout = HEALTH_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.useCaches = false
            connection.responseCode == HttpURLConnection.HTTP_OK &&
                connection.inputStream.bufferedReader().use { it.readText().trim() == "ok" }
        } catch (_: IOException) {
            false
        } finally {
            connection.disconnect()
        }
    }
}
