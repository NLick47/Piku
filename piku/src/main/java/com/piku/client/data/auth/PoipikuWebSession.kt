package com.piku.client.data.auth

import android.webkit.CookieManager
import com.piku.client.data.remote.ApiConfig

/** poipiku 网页登录的会话：判据只有 WebView 手里那份 cookie 头，非空才算登录 */
object PoipikuWebSession {
    const val LOGIN_PAGE_URL = ApiConfig.BASE_URL + "LoginFormEmailPcV.jsp"

    fun sessionToken(cookieHeader: String?): String? {
        if (cookieHeader.isNullOrBlank()) return null
        return cookieHeader.split(';')
            .firstOrNull { it.trim().startsWith("$SESSION_COOKIE=") }
            ?.substringAfter('=')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun isLoggedIn(cookieHeader: String?): Boolean = sessionToken(cookieHeader) != null

    /**
     * 过期掉 WebView 里那份会话 cookie。
     *
     * 只点这一个 cookie 名：WebView 的 cookie 罐是全局的，removeAllCookies 会把用户在别处的网页登录一起清掉。
     * 必须挂在登出上——应用侧登出只清自己的 cookie 存储，WebView 那份还留着的话，
     * 再进登录页会被站点直接认成已登录。
     */
    fun clearWebViewCookies() {
        runCatching {
            CookieManager.getInstance().apply {
                setCookie(ApiConfig.BASE_URL, "$SESSION_COOKIE=; Max-Age=0; Path=/")
                flush()
            }
        }
    }

    private const val SESSION_COOKIE = ApiConfig.SESSION_COOKIE
}