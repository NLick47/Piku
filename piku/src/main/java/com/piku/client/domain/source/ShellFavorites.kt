package com.piku.client.domain.source

import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkKey
import kotlinx.coroutines.flow.Flow

/**
 * 通用壳需要的收藏能力：收藏键流 + 切换。按源无关的设计，键自带源命名空间。
 * 由 DI 用仓库实现；单测可直接给假实现，壳本身不依赖任何仓库/数据库。
 */
interface ShellFavorites {
    val favoriteIds: Flow<Set<WorkKey>>
    suspend fun toggle(work: Work): Boolean
}
