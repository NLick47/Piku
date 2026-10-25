package com.piku.client.source.ao3.data.auth

import java.net.URI

/** 登录页与登录回包的纯解析：不碰网络，便于用真实页面做回归 */
internal object Ao3AuthHtml {

    private val CSRF_META = Regex("""<meta name="csrf-token" content="([^"]+)"""")

    private val AUTH_ERROR_PATH = "/auth_error"

    private val USERS_PREFIX = "/users/"

    /** 登录页的 csrf-token。表单里的 authenticity_token 是同一份令牌的掩码，取 meta 更稳 */
    fun csrfToken(html: String): String? =
        CSRF_META.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    /** 账号或密码被拒时站点把用户丢到 /auth_error */
    fun isAuthError(location: String?): Boolean =
        location?.substringBefore('?')?.endsWith(AUTH_ERROR_PATH) == true

    /** 成功登录的落地页 /users/<用户名>；不是用户页给 null */
    fun username(location: String?): String? {
        if (location.isNullOrBlank()) return null
        val path = runCatching { URI(location).path }.getOrNull() ?: location.substringBefore('?')
        if (!path.startsWith(USERS_PREFIX)) return null
        return path.removePrefix(USERS_PREFIX).substringBefore('/').takeIf { it.isNotBlank() }
    }
}

internal sealed interface Ao3LoginOutcome {
    data class Success(val username: String) : Ao3LoginOutcome
    data object InvalidCredentials : Ao3LoginOutcome

    /** 既没进用户页也没进 /auth_error：表单过期、被机器人页拦下等 */
    data object Rejected : Ao3LoginOutcome
}

internal fun classifyLogin(code: Int, location: String?): Ao3LoginOutcome = when {
    code == 302 && Ao3AuthHtml.isAuthError(location) -> Ao3LoginOutcome.InvalidCredentials
    code == 302 -> Ao3AuthHtml.username(location)
        ?.let { Ao3LoginOutcome.Success(it) }
        ?: Ao3LoginOutcome.Rejected
    else -> Ao3LoginOutcome.Rejected
}
