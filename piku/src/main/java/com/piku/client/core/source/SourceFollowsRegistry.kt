package com.piku.client.core.source

import com.piku.client.core.model.WorkSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceFollowsRegistry @Inject constructor(
    follows: Set<@JvmSuppressWildcards SourceFollows>,
) {
    private val byId: Map<WorkSource, SourceFollows> = follows.associateBy { it.sourceId }

    fun byId(source: WorkSource): SourceFollows? = byId[source]
}
