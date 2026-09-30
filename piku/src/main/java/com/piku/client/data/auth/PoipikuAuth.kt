package com.piku.client.data.auth

import com.piku.client.R
import com.piku.client.data.repository.AuthRepository
import com.piku.client.data.repository.ProfileRepository
import com.piku.client.data.repository.SessionRuntime
import com.piku.client.domain.model.AuthStatus
import com.piku.client.domain.model.UserProfile
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.SourceAccount
import com.piku.client.domain.source.SourceAuth
import com.piku.client.domain.source.SourceAuthRoutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * poipiku 的登录插件：把既有的 [AuthRepository] 与 [ProfileRepository] 适配成插件协议。
 *
 * 自己不带状态、不带存储、不改任何流程——poipiku 的行为与接入插件层之前一致：
 * 登录态来自会话，账号展示来自资料缓存（冷启动先给缓存、再后台刷新）。
 */
@Singleton
class PoipikuAuth @Inject constructor(
    private val repository: AuthRepository,
    profileRepository: ProfileRepository,
    runtime: SessionRuntime,
) : SourceAuth {

    override val source = WorkSource.POIPIKU

    override val status: StateFlow<AuthStatus> get() = repository.authStatus

    /**
     * 资料是异步补的：已登录但资料未到时给 null，外壳据此画骨架——
     * 不能把 null 当成"未登录"。
     */
    override val account: StateFlow<SourceAccount?> = profileRepository.userProfile
        .map { it?.toSourceAccount() }
        .stateIn(
            CoroutineScope(SupervisorJob() + runtime.dispatcher),
            SharingStarted.WhileSubscribed(REFRESH_GRACE_MS),
            profileRepository.userProfile.value?.toSourceAccount(),
        )

    override val loginRoute: String = SourceAuthRoutes.POIPIKU_LOGIN

    override val logoutMessageRes = R.string.account_logout_message

    /** 账号主页：uid 能解析出来就给（外壳自己拼路由） */
    override fun profileId(account: SourceAccount): String? =
        account.account.takeIf { it.isNotBlank() }

    override fun logout() = repository.logout()

    private companion object {
        const val REFRESH_GRACE_MS = 5_000L
    }
}

/** 资料 → 账号投影。uid 当作站内标识（pixiv 那边对应 @账号名） */
internal fun UserProfile.toSourceAccount(): SourceAccount = SourceAccount(
    displayName = name.orEmpty(),
    account = uid.orEmpty(),
    avatarUrl = avatarUrl,
)
