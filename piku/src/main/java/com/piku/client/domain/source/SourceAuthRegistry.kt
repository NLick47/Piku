package com.piku.client.domain.source

import com.piku.client.domain.model.WorkSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceAuthRegistry @Inject constructor(
    auths: Set<@JvmSuppressWildcards SourceAuth>,
) {
    private val byId: Map<WorkSource, SourceAuth> = auths.associateBy { it.source }

    fun byId(source: WorkSource): SourceAuth? = byId[source]

    /** 已注册的插件，供需要逐源渲染的外壳用 */
    val all: List<SourceAuth> get() = byId.values.toList()

    /** 登录门统一问这里；没注册的源一律未登录（宁可锁着，也不放一个假登录过去） */
    fun isLoggedIn(source: WorkSource): Boolean = byId[source]?.isLoggedIn() == true
}
