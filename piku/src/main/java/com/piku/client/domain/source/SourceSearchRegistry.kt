package com.piku.client.domain.source

import com.piku.client.domain.model.WorkSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceSearchRegistry @Inject constructor(
    searches: Set<@JvmSuppressWildcards SourceSearch>,
) {
    private val byId: Map<WorkSource, SourceSearch> = searches.associateBy { it.sourceId }

    /** 查不到给 null：外壳据此走默认搜索链路 */
    fun byIdOrNull(id: WorkSource): SourceSearch? = byId[id]
}
