package com.piku.client.data.repository

import com.piku.client.data.local.FavoriteEntity
import com.piku.client.data.local.FavoriteFolderEntity
import com.piku.client.data.local.FavoriteMembershipEntity
import com.piku.client.domain.model.FavoriteSyncData
import com.piku.client.domain.model.WorkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteSyncRoundTripTest {

    private val now = 1_000_000L

    private fun folder(id: Long, name: String, isDefault: Boolean = false) =
        FavoriteFolderEntity(id = id, name = name, createdAt = now - 100, isDefault = isDefault)

    private fun localWork(workId: String) = FavoriteEntity(
        source = WorkSource.POIPIKU,
        workId = workId,
        authorId = 1L,
        title = "标题$workId",
        authorName = "作者",
        thumbnailUrl = "https://x/$workId.jpg",
        authorAvatarUrl = null,
        imageCount = 1,
        r18 = false,
        addedAt = now - 50,
    )

    private fun membership(folderId: Long, workId: String) =
        FavoriteMembershipEntity(
            folderId = folderId,
            source = WorkSource.POIPIKU,
            workId = workId,
            addedAt = now - 50,
        )

    private fun workIds(data: FavoriteSyncData) = data.memberships.map { it.workId }.sorted()

    @Test
    fun secondSyncAfterLandingOnForeignIdsStaysStable() {
        // A：两个夹（id 1、2），w1 在默认夹、w2 在同人
        val fromA = FavoriteSyncMerge.merge(
            localFolders = listOf(folder(1L, "默认收藏夹", isDefault = true), folder(2L, "同人")),
            localFavorites = listOf(localWork("w1"), localWork("w2")),
            localMemberships = listOf(1L to membership(1L, "w1"), 2L to membership(2L, "w2")),
            remote = null,
            snapshot = null,
            now = now,
        )

        // B 本机：同名夹是另外两个 id（7、9），把云端这份落下来
        val bFolders = listOf(folder(7L, "默认收藏夹", isDefault = true), folder(9L, "同人"))
        val landed = FavoriteSyncWritePlan.membershipTargets(
            fromA,
            bFolders.associate { it.name to it.id },
        )
        assertEquals(
            "归属要落到 B 自己的夹上",
            listOf(7L to "w1", 9L to "w2"),
            landed.map { it.folderId to it.membership.workId },
        )

        // B 再同步一次：本地状态是"自己的夹 + 刚落下的归属"，云端和快照都是 A 那份
        val fromB = FavoriteSyncMerge.merge(
            localFolders = bFolders,
            localFavorites = listOf(localWork("w1"), localWork("w2")),
            localMemberships = landed.map { it.folderId to membership(it.folderId, it.membership.workId) },
            remote = fromA,
            snapshot = fromA,
            now = now + 1,
        )

        assertTrue("B 不能凭空出墓碑，那会把云端的收藏当删除清掉", fromB.tombstones.isEmpty())
        assertEquals("两个夹都留着", listOf("默认收藏夹", "同人"), fromB.folders.map { it.name })
        assertEquals("归属一个不少", listOf("w1", "w2"), workIds(fromB))
        assertEquals("夹 id 不许重号", 2, fromB.folders.map { it.id }.distinct().size)
    }

    @Test
    fun syncingTwiceInARowChangesNothing() {
        // 同一台设备连续同步两次：第二次的远端/快照就是上一次上传的那份，结果必须原样
        val first = FavoriteSyncMerge.merge(
            localFolders = listOf(folder(1L, "默认收藏夹", isDefault = true)),
            localFavorites = listOf(localWork("w1")),
            localMemberships = listOf(1L to membership(1L, "w1")),
            remote = null,
            snapshot = null,
            now = now,
        )

        val second = FavoriteSyncMerge.merge(
            localFolders = listOf(folder(1L, "默认收藏夹", isDefault = true)),
            localFavorites = listOf(localWork("w1")),
            localMemberships = listOf(1L to membership(1L, "w1")),
            remote = first,
            snapshot = first,
            now = now + 1,
        )

        assertEquals(first.folders, second.folders)
        assertEquals(first.works, second.works)
        assertEquals(first.memberships, second.memberships)
        assertTrue("没有发生过删除，就不该出墓碑", second.tombstones.isEmpty())
    }
}
