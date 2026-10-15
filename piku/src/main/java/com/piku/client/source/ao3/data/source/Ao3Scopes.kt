package com.piku.client.source.ao3.data.source

import com.piku.client.R
import com.piku.client.core.model.WorkSource
import com.piku.client.core.source.SourceScope
import com.piku.client.core.source.SourceScopeKind
import com.piku.client.core.source.SourceScopeSuggestion
import com.piku.client.core.source.SourceScopes
import com.piku.client.source.ao3.data.local.Ao3ScopeStore
import com.piku.client.source.ao3.data.remote.Ao3ScopeParams
import com.piku.client.source.ao3.data.repository.Ao3Repository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AO3 的圈层能力：同人 / 配对 / 标签三个维度，联想词取自站点的标签搜索
 * （只回规范标签，按使用量降序），客户端不维护任何名单。
 */
@Singleton
class Ao3Scopes @Inject constructor(
    private val repository: Ao3Repository,
    private val store: Ao3ScopeStore,
) : SourceScopes {

    override val sourceId = WorkSource.AO3

    override val kinds = listOf(
        SourceScopeKind(KIND_FANDOM, R.string.ao3_scope_fandom),
        SourceScopeKind(KIND_RELATIONSHIP, R.string.ao3_scope_relationship),
        SourceScopeKind(KIND_FREEFORM, R.string.ao3_scope_freeform),
    )

    override val scopes: StateFlow<List<SourceScope>> = store.scopes

    override val active: StateFlow<SourceScope?> = store.active

    /** 见 [ao3ChipLabel] */
    override fun chipLabel(scope: SourceScope): String = ao3ChipLabel(scope)

    override suspend fun suggest(kind: String, term: String): Result<List<SourceScopeSuggestion>> {
        val trimmed = term.trim()
        val type = Ao3ScopeParams.tagTypeOf(kind)
        if (trimmed.isEmpty() || type.isEmpty()) return Result.success(emptyList())
        return repository.tagSuggestions(trimmed, type)
            .map { found -> found.map { SourceScopeSuggestion(it.name, it.count) } }
    }

    override fun setScopes(scopes: List<SourceScope>) = store.setScopes(scopes)

    override fun setActive(scope: SourceScope) = store.setActive(scope)

    companion object {
        const val KIND_FANDOM = "fandom"
        const val KIND_RELATIONSHIP = "relationship"
        const val KIND_FREEFORM = "freeform"
    }
}

/**
 * 同人圈层的短名：切掉「 - 」起的消歧后缀（"Harry Potter - J. K. Rowling" → "Harry Potter"）。
 * 只对同人做——配对与自由标签里的连字符是名字的一部分，切了会改义。
 */
internal fun ao3ChipLabel(scope: SourceScope): String {
    if (scope.kind != Ao3Scopes.KIND_FANDOM) return scope.name
    val head = scope.name.substringBefore(" - ").trim()
    return head.ifEmpty { scope.name }
}
