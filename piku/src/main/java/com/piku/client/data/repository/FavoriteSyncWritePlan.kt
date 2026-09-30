package com.piku.client.data.repository

import com.piku.client.domain.model.FavoriteSyncData
import com.piku.client.domain.model.SyncMembership

internal object FavoriteSyncWritePlan {

    /** 一条归属要落到本机哪个收藏夹 */
    data class MembershipTarget(
        val folderId: Long,
        val membership: SyncMembership,
    )

    fun membershipTargets(
        data: FavoriteSyncData,
        localFolderIdByName: Map<String, Long>,
    ): List<MembershipTarget> {
        val namesByPayloadId = data.folders.groupBy({ it.id }, { it.name })
        return data.memberships.mapNotNull { membership ->
            // 同一个 id 对上一个夹才认得出这条归属是哪个夹的；重号的 payload 整条丢掉，
            // 免得串到别的夹里去（重号来自旧客户端按 id 合并的结果）
            val folderName = namesByPayloadId[membership.folderId]?.singleOrNull() ?: return@mapNotNull null
            val localFolderId = localFolderIdByName[folderName] ?: return@mapNotNull null
            MembershipTarget(localFolderId, membership)
        }
    }
}
