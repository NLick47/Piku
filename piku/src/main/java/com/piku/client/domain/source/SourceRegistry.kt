package com.piku.client.domain.source

import com.piku.client.domain.model.WorkSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceRegistry @Inject constructor(
    sources: Set<@JvmSuppressWildcards ContentSource>,
) {
    private val byId: Map<WorkSource, ContentSource> = sources.associateBy { it.id }

    fun byId(id: WorkSource): ContentSource =
        byId[id] ?: error("未注册的内容源: $id")

    /** 查不到给 null：外壳给"可能没有内容源"的地方用（如账号页按源渲染） */
    fun byIdOrNull(id: WorkSource): ContentSource? = byId[id]

    /**
     * 作者页形态：从 FollowUser 入口（我的关注/搜索用户）点进作者页时用，
     * 那条路上没有 Work 可问，只能按源问。未注册的源退回默认形态。
     */
    fun authorPageStyle(id: WorkSource): AuthorPageStyle =
        byId[id]?.authorPageStyle ?: AuthorPageStyle.Works

    /** 供换源控件列出可选源 */
    val all: List<ContentSource> get() = byId.values.toList()
}
