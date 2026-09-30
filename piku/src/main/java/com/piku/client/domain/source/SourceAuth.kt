package com.piku.client.domain.source

import androidx.annotation.StringRes
import com.piku.client.domain.model.AuthStatus
import com.piku.client.domain.model.WorkSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 一个源的登录能力（登录插件协议）。
 *
 * **各源平权**：谁都不是"主账号"，每个源自己声明它那套账号管理长什么样——
 * 登录页在哪、断开自己这个动作叫什么、有没有账号主页。外壳只照做，不区分角色。
 *
 * 凭据形态也由实现自己决定（poipiku 是邮箱密码 + 会话 cookie，pixiv 是 OAuth 令牌），
 * 各自存储、各自清除，互不可见。
 */
interface SourceAuth {

    val source: WorkSource

    /** 本源的登录态。登录成功、登出、令牌被动失效都会推新值 */
    val status: StateFlow<AuthStatus>

    /**
     * 登录账号的展示信息，未登录给 null。
     * 可选能力：只有需要在外壳里露脸的源才覆写（登录态本身不依赖它）。
     */
    val account: StateFlow<SourceAccount?>
        get() = NO_ACCOUNT

    /**
     * 本源的登录页路由（纯字符串，不引导航库）。
     * null = 该源没有应用内登录页，外壳不给「去登录」入口。
     */
    val loginRoute: String?

    /**
     * 退出前的确认文案（格式串，`%1$s` = 源名）。
     * 所有源的这个动作都叫「退出登录」；但话说不清退出的是谁、会怎样，
     * 用户就容易误以为退的是全部。
     */
    @get:StringRes val logoutMessageRes: Int

    /**
     * 本源有"账号主页"时，给出它的账号 id（外壳据此拼自己的路由）；null = 没有主页可看。
     * 与 [loginRoute] 同一个思路：能力由源声明，外壳不猜。
     */
    fun profileId(account: SourceAccount): String? = null

    fun isLoggedIn(): Boolean = status.value == AuthStatus.LOGGED_IN

    /** 只清本源的凭据与会话；不得触碰其它源 */
    fun logout()
}

/** 账号展示投影：谁登录了。凭据（令牌/密码/会话）一律不进这个模型 */
data class SourceAccount(
    val displayName: String,
    /** 站内标识：poipiku 是 uid，pixiv 是账号名。空 = 该源没有这个概念 */
    val account: String = "",
    val avatarUrl: String? = null,
)

/** 不实现 [SourceAuth.account] 的源共用这一份空流：单例，订阅它不会每次重建 */
private val NO_ACCOUNT: StateFlow<SourceAccount?> = MutableStateFlow(null)

/** 各源登录页的路由名。放在域层是为了让插件自己声明入口，而不是让外壳穷举 */
object SourceAuthRoutes {
    const val POIPIKU_LOGIN = "login"
    const val PIXIV_LOGIN = "pixiv_login"
}
