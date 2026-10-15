package com.piku.client.core.source

import androidx.annotation.StringRes
import com.piku.client.core.model.WorkSource
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SourceScope(val kind: String, val name: String) {
    val id: String get() = "$kind:$name"
}

/**
 * 圈层选择器里的一个维度（同人/配对/标签） */
data class SourceScopeKind(val id: String, @StringRes val labelRes: Int)

/** 订阅数软上限：每多一路订阅就多一路请求，直连抖动下失败率随之放大 */
const val MAX_SCOPES = 8

/** 是否还能再订：满了之后选择器只读（移除照常）。纯判定，UI 与测试共用 */
fun canAddScope(current: List<SourceScope>, max: Int = MAX_SCOPES): Boolean =
    current.size < max

/**
 * chips 显示名：先按源规则收短，收短后与同批其它圈层重名的一律回退全名——
 * 两颗看着一样的 chip 比长名字更糟。返回顺序与 [scopes] 一致。
 */
fun scopeChipLabels(scopes: List<SourceScope>, shorten: (SourceScope) -> String): List<String> {
    val short = scopes.map(shorten)
    val duplicated = short.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    return scopes.mapIndexed { index, scope ->
        if (short[index] in duplicated) scope.name else short[index]
    }
}

/**
 * 联想的一条候选。作品数是给用户区分同名圈层用的（同名标签可能一个是同人、
 * 一个是配对），站点给不出时为 null，选择器只显示拿得到的。
 */
data class SourceScopeSuggestion(val name: String, val count: Int? = null)

/**
 * 圈层式首页的源能力：源声明自己有哪些圈层维度、给得上联想词、并持有"我订阅的圈层"。
 * 没注册的源首页保持通用流式壳，行为不变（同 SourceAuth 按能力注册的那套）。
 */
interface SourceScopes {

    val sourceId: WorkSource

    /** 圈层维度；空 = 该源没有圈层式首页 */
    val kinds: List<SourceScopeKind>

    /** 联想：按维度找圈层名（AO3 走站点的标签搜索，客户端不维护名单） */
    suspend fun suggest(kind: String, term: String): Result<List<SourceScopeSuggestion>>

    /** 已订阅的圈层（落盘）；顺序即 chips 顺序 */
    val scopes: StateFlow<List<SourceScope>>

    /** 当前圈层：重开 App 回到上次那个 */
    val active: StateFlow<SourceScope?>

    /** chips 上的显示名：长名可收短（同人去掉「 - 作者」消歧后缀）；撞名回退全名由外壳兜底 */
    fun chipLabel(scope: SourceScope): String = scope.name

    fun setScopes(scopes: List<SourceScope>)

    fun setActive(scope: SourceScope)
}

@Singleton
class SourceScopeRegistry @Inject constructor(
    scopes: Set<@JvmSuppressWildcards SourceScopes>,
) {

    private val byId = scopes.associateBy { it.sourceId }

    /** null = 该源不是圈层式首页 */
    fun byId(source: WorkSource): SourceScopes? = byId[source]
}
