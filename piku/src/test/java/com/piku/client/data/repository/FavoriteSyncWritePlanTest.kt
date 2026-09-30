package com.piku.client.data.repository

import com.piku.client.domain.model.FavoriteSyncData
import com.piku.client.domain.model.SyncFolder
import com.piku.client.domain.model.SyncMembership
import com.piku.client.domain.model.WorkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteSyncWritePlanTest {

    private fun payload(
        folders: List<SyncFolder>,
        memberships: List<SyncMembership>,
    ) = FavoriteSyncData(
        version = FavoriteSyncData.CURRENT_VERSION,
        syncedAt = 1L,
        folders = folders,
        works = emptyList(),
        memberships = memberships,
    )

    private fun membership(folderId: Long, workId: String = "123") = SyncMembership(
        folderId = folderId,
        source = WorkSource.POIPIKU.name,
        workId = workId,
        addedAt = 9L,
    )

    @Test
    fun membershipLandsOnTheLocalFolderOfTheSameName() {
        // 两台设备各自把「同人」建成第 2 个夹：云端那份说 folderId=5，本机那个夹是 2
        val data = payload(
            folders = listOf(SyncFolder(id = 5L, name = "同人", isDefault = false, createdAt = 1L)),
            memberships = listOf(membership(folderId = 5L)),
        )

        val targets = FavoriteSyncWritePlan.membershipTargets(data, mapOf("同人" to 2L))

        assertEquals("归属要用本机那个夹的 id", 2L, targets.single().folderId)
        assertEquals("123", targets.single().membership.workId)
    }

    @Test
    fun membershipForAFolderMissingLocallyIsDropped() {
        val data = payload(
            folders = listOf(SyncFolder(id = 5L, name = "同人", isDefault = false, createdAt = 1L)),
            memberships = listOf(membership(folderId = 5L)),
        )

        assertTrue(FavoriteSyncWritePlan.membershipTargets(data, emptyMap()).isEmpty())
    }

    @Test
    fun ambiguousPayloadIdsAreDroppedRatherThanMisfiled() {
        // 旧客户端按 id 合并出的 payload 会让两个夹共用一个 id：谁都认不出这条归属是哪个夹的，
        // 宁可丢掉也不能塞进随便一个夹里
        val data = payload(
            folders = listOf(
                SyncFolder(id = 2L, name = "同人", isDefault = false, createdAt = 1L),
                SyncFolder(id = 2L, name = "漫画", isDefault = false, createdAt = 1L),
            ),
            memberships = listOf(membership(folderId = 2L)),
        )

        assertTrue(
            FavoriteSyncWritePlan.membershipTargets(data, mapOf("同人" to 5L, "漫画" to 9L)).isEmpty(),
        )
    }

    @Test
    fun membershipsOfDifferentFoldersKeepTheirOwnTargets() {
        val data = payload(
            folders = listOf(
                SyncFolder(id = 1L, name = "默认收藏夹", isDefault = true, createdAt = 1L),
                SyncFolder(id = 5L, name = "同人", isDefault = false, createdAt = 1L),
            ),
            memberships = listOf(membership(folderId = 5L, workId = "a"), membership(folderId = 1L, workId = "b")),
        )

        val targets = FavoriteSyncWritePlan.membershipTargets(
            data,
            mapOf("默认收藏夹" to 7L, "同人" to 2L),
        )

        assertEquals(
            listOf("a" to 2L, "b" to 7L),
            targets.map { it.membership.workId to it.folderId },
        )
    }
}
