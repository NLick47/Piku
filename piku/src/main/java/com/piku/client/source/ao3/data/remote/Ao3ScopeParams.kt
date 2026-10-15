package com.piku.client.source.ao3.data.remote

import com.piku.client.core.source.SourceScope
import com.piku.client.source.ao3.data.source.Ao3Scopes

/**
 * 圈层 → 搜索端点的收窄参数。维度决定用哪个字段（同人/配对/标签各一个），
 * 值直接用站点标签名——与搜索表单里那些字段同一套语义。
 */
internal object Ao3ScopeParams {

    fun of(scope: SourceScope?): Map<String, String> = when (scope?.kind) {
        Ao3Scopes.KIND_FANDOM -> mapOf("work_search[fandom_names]" to scope.name)
        Ao3Scopes.KIND_RELATIONSHIP -> mapOf("work_search[relationship_names]" to scope.name)
        Ao3Scopes.KIND_FREEFORM -> mapOf("work_search[freeform_names]" to scope.name)
        else -> emptyMap()
    }

    /** 维度 → 标签搜索的类型值（大写开头）。类型传错会回一堆别的维度的同名标签 */
    fun tagTypeOf(kind: String): String = when (kind) {
        Ao3Scopes.KIND_FANDOM -> "Fandom"
        Ao3Scopes.KIND_RELATIONSHIP -> "Relationship"
        Ao3Scopes.KIND_FREEFORM -> "Freeform"
        else -> ""
    }
}
