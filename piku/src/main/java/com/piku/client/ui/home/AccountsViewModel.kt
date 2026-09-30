package com.piku.client.ui.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.domain.model.AuthStatus
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.SourceAccount
import com.piku.client.domain.source.SourceAuth
import com.piku.client.domain.source.SourceAuthRegistry
import com.piku.client.domain.source.SourceRegistry
import com.piku.client.domain.usecase.ObserveHomeSourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SourceAccountRow(
    val source: WorkSource,
    @StringRes val labelRes: Int,
    /** 该源的登录页路由；null = 还没接入应用内登录 */
    val loginRoute: String?,
    /** 退出前的确认文案（`%1$s` = 源名）；null = 该源没有退出动作（如还没接插件） */
    @StringRes val logoutMessageRes: Int?,
    /** 有账号主页时给得出它的 id（外壳据此拼路由）；null = 没主页可看 */
    val profileId: String?,
    val loggedIn: Boolean,
    /** 账号投影；已登录但资料还没到时为 null */
    val account: SourceAccount?,
) {
    /** 已登录但资料未到：画骨架，别显示成"未登录" */
    val pending: Boolean get() = loggedIn && account == null
}

/**
 * 账号数据源：账号页一行一个源，抽屉头部另取「当前首页源」那一行。
 *
 * 逐源问插件，不为任何站点写特例——新源注册进 [SourceAuthRegistry] 就自动多一行。
 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val authRegistry: SourceAuthRegistry,
    private val sourceRegistry: SourceRegistry,
    observeHomeSourceUseCase: ObserveHomeSourceUseCase,
) : ViewModel() {

    /**
     * 没有内容源的插件不列：账号页每行都要能点名它属于哪块内容。
     * 顺序按源枚举，**不表达任何含义**（谁都不是主账号）。
     */
    private val plugins: List<SourceAuth> = authRegistry.all
        .filter { sourceRegistry.byIdOrNull(it.source) != null }
        .sortedBy { it.source.ordinal }

    private val pluginRows: List<Flow<SourceAccountRow>> =
        plugins.map { plugin -> plugin.row(sourceRegistry) }

    /** 账号页：一行一个源 */
    val rows: StateFlow<List<SourceAccountRow>> =
        if (pluginRows.isEmpty()) {
            // combine 收到空表就永不发射，这条路单独给一个空流
            MutableStateFlow(emptyList())
        } else {
            combine(pluginRows) { it.toList() }
                .stateIn(
                    viewModelScope,
                    SharingStarted.Eagerly,
                    plugins.map { it.snapshot(sourceRegistry) },
                )
        }

    /** 抽屉头部：只显示当前首页源的那一行，切源就跟着换 */
    val current: StateFlow<SourceAccountRow?> =
        combine(rows, observeHomeSourceUseCase()) { list, source ->
            // 该源还没接登录插件时也补一行：头部不能永远停在骨架上
            list.firstOrNull { it.source == source } ?: source.withoutPluginRow()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun logout(source: WorkSource) {
        authRegistry.byId(source)?.logout()
    }

    private fun WorkSource.withoutPluginRow(): SourceAccountRow? {
        val content = sourceRegistry.byIdOrNull(this) ?: return null
        return SourceAccountRow(
            source = this,
            labelRes = content.labelRes,
            loginRoute = null,
            logoutMessageRes = null,
            profileId = null,
            loggedIn = false,
            account = null,
        )
    }

    /** 登录态或账号变了就重发一行 */
    private fun SourceAuth.row(sourceRegistry: SourceRegistry): Flow<SourceAccountRow> =
        combine(status, account) { status, account ->
            build(sourceRegistry, status == AuthStatus.LOGGED_IN, account)
        }

    private fun SourceAuth.snapshot(sourceRegistry: SourceRegistry): SourceAccountRow =
        build(sourceRegistry, isLoggedIn(), account.value)

    private fun SourceAuth.build(
        sourceRegistry: SourceRegistry,
        loggedIn: Boolean,
        account: SourceAccount?,
    ): SourceAccountRow {
        val current = account.takeIf { loggedIn }
        return SourceAccountRow(
            source = source,
            labelRes = sourceRegistry.byId(source).labelRes,
            loginRoute = loginRoute,
            logoutMessageRes = logoutMessageRes,
            profileId = current?.let { profileId(it) },
            loggedIn = loggedIn,
            account = current,
        )
    }
}
