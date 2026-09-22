package com.piku.client.data.repository

import com.piku.client.data.local.FavoriteEntity
import com.piku.client.data.local.FavoriteFolderEntity
import com.piku.client.data.local.FavoriteMembershipEntity
import com.piku.client.domain.model.FavoriteSyncData
import com.piku.client.domain.model.SyncFolder
import com.piku.client.domain.model.SyncMembership
import com.piku.client.domain.model.SyncTombstone
import com.piku.client.domain.model.SyncWork
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File


class FavoriteSyncMergeTest {

    private val now = 1_000_000_000_000L

    private fun folder(id: Long, name: String, createdAt: Long = now - 10_000) =
        FavoriteFolderEntity(id = id, name = name, createdAt = createdAt)

    private fun membership(folderId: Long, workId: String, addedAt: Long = now - 5_000) =
        FavoriteMembershipEntity(folderId = folderId, workId = workId, addedAt = addedAt)

    private fun localWork(workId: String, addedAt: Long = now - 5_000) = FavoriteEntity(
        workId = workId,
        authorId = 1L,
        title = "标题$workId",
        authorName = "作者",
        thumbnailUrl = "https://x/$workId.jpg",
        authorAvatarUrl = null,
        imageCount = 3,
        r18 = false,
        addedAt = addedAt,
        contentBackedUp = false,
    )

    private fun remoteFolder(id: Long, name: String, createdAt: Long = now - 10_000) =
        SyncFolder(id = id, name = name, isDefault = false, createdAt = createdAt)

    private fun remoteWork(workId: String, addedAt: Long = now - 5_000) = SyncWork(
        workId = workId,
        authorId = 1L,
        title = "标题$workId",
        authorName = "作者",
        thumbnailUrl = "https://x/$workId.jpg",
        authorAvatarUrl = null,
        imageCount = 3,
        r18 = false,
        addedAt = addedAt,
    )

    private fun remoteMembership(folderId: Long, workId: String, addedAt: Long = now - 5_000) =
        SyncMembership(folderId = folderId, workId = workId, addedAt = addedAt)

    private fun payload(
        folders: List<SyncFolder> = emptyList(),
        works: List<SyncWork> = emptyList(),
        memberships: List<SyncMembership> = emptyList(),
        tombstones: List<SyncTombstone> = emptyList(),
    ) = FavoriteSyncData(
        syncedAt = now - 60_000,
        folders = folders,
        works = works,
        memberships = memberships,
        tombstones = tombstones,
    )

    private fun merge(
        localFolders: List<FavoriteFolderEntity> = emptyList(),
        localFavorites: List<FavoriteEntity> = emptyList(),
        localMemberships: List<Pair<Long, FavoriteMembershipEntity>> = emptyList(),
        remote: FavoriteSyncData? = null,
        snapshot: FavoriteSyncData? = null,
    ) = FavoriteSyncMerge.merge(
        localFolders = localFolders,
        localFavorites = localFavorites,
        localMemberships = localMemberships,
        remote = remote,
        snapshot = snapshot,
        now = now,
    )

    @Test
    fun deletedFolderIsNotRestoredFromRemote() {
        // 上一次同步：默认夹（本地一定有，仓库层不允许删）和夹 A，A 里有作品 w1
        val snapshot = payload(
            folders = listOf(remoteFolder(99L, "默认收藏夹"), remoteFolder(1L, "A")),
            works = listOf(remoteWork("w1")),
            memberships = listOf(remoteMembership(1L, "w1")),
        )
        // 本机把夹 A 删了，远端还留着
        val result = merge(
            localFolders = listOf(folder(99L, "默认收藏夹")),
            remote = snapshot,
            snapshot = snapshot,
        )

        assertEquals("删除的夹不能复活", listOf("默认收藏夹"), result.folders.map { it.name })
        assertTrue("夹里的归属也要一起删掉", result.memberships.isEmpty())
        assertTrue("失去全部归属的作品不再上传", result.works.isEmpty())
        assertEquals(listOf(SyncTombstone.folder("A", now)), result.tombstones)
    }

    @Test
    fun otherDeviceAdditionsAreKept() {
        // 本机有夹 A；另一台设备新增了夹 B 和 B 里的作品
        val local = listOf(folder(1L, "A"))
        val snapshot = payload(folders = listOf(remoteFolder(1L, "A")))
        val remote = payload(
            folders = listOf(remoteFolder(1L, "A"), remoteFolder(2L, "B")),
            works = listOf(remoteWork("w9")),
            memberships = listOf(remoteMembership(2L, "w9")),
        )

        val result = merge(
            localFolders = local,
            localMemberships = emptyList(),
            remote = remote,
            snapshot = snapshot,
        )

        assertEquals(listOf("A", "B"), result.folders.map { it.name })
        assertEquals(listOf("w9"), result.memberships.map { it.workId })
        assertEquals("另一台设备的新增不能被误判成删除", emptyList<SyncTombstone>(), result.tombstones)
    }

    @Test
    fun removedWorkIsNotRestored() {
        // 夹 A 里有 w1、w2，本机把 w2 移出了
        val snapshot = payload(
            folders = listOf(remoteFolder(1L, "A")),
            memberships = listOf(remoteMembership(1L, "w1"), remoteMembership(1L, "w2")),
        )
        val result = merge(
            localFolders = listOf(folder(1L, "A")),
            localMemberships = listOf(1L to membership(1L, "w1")),
            remote = snapshot,
            snapshot = snapshot,
        )

        assertEquals(listOf("w1"), result.memberships.map { it.workId })
        assertEquals(listOf(SyncTombstone.membership("A", "w2", now)), result.tombstones)
    }

    @Test
    fun renameDoesNotDuplicateFolder() {
        // 本机把 A 改名成 B：快照里是 A，本地现在是 B，远端还叫 A
        val snapshot = payload(
            folders = listOf(remoteFolder(1L, "A")),
            memberships = listOf(remoteMembership(1L, "w1")),
        )
        val result = merge(
            localFolders = listOf(folder(7L, "B")),
            localMemberships = listOf(7L to membership(7L, "w1")),
            remote = snapshot,
            snapshot = snapshot,
        )

        assertEquals("改名不能变成两个夹", listOf("B"), result.folders.map { it.name })
        assertEquals(listOf("w1"), result.memberships.map { it.workId })
        assertEquals(listOf("B"), result.memberships.map { it.folderId }.distinct().map { id ->
            result.folders.first { it.id == id }.name
        })
        assertEquals(listOf(SyncTombstone.folder("A", now)), result.tombstones)
    }

    @Test
    fun reAddedAfterDeleteSurvives() {
        // 夹 A 里的 w1 被删过（墓碑时间 = now - 1000），之后又加回来了（addedAt 更晚）
        val tombstone = SyncTombstone.membership("A", "w1", now - 1_000)
        val result = merge(
            localFolders = listOf(folder(1L, "A")),
            localMemberships = listOf(1L to membership(1L, "w1", addedAt = now - 500)),
            remote = payload(folders = listOf(remoteFolder(1L, "A"))),
            snapshot = payload(tombstones = listOf(tombstone)),
        )

        assertEquals("删除后重新添加的条目要留下", listOf("w1"), result.memberships.map { it.workId })
    }

    @Test
    fun remoteTombstoneReachesLocalCopy() {
        // 另一台设备删了夹 A，本机还留着
        val result = merge(
            localFolders = listOf(folder(1L, "A")),
            localMemberships = listOf(1L to membership(1L, "w1")),
            remote = payload(
                folders = emptyList(),
                tombstones = listOf(SyncTombstone.folder("A", now - 2_000)),
            ),
            snapshot = payload(folders = listOf(remoteFolder(1L, "A"))),
        )

        assertTrue("别的设备删掉的，本机也要跟着删", result.folders.isEmpty())
        assertTrue(result.memberships.isEmpty())
        assertEquals(listOf(SyncTombstone.folder("A", now - 2_000)), result.tombstones)
    }

    @Test
    fun withoutSnapshotMergeStaysAdditive() {
        // 全新安装：没有快照，远端的一切都要拿回来
        val remote = payload(
            folders = listOf(remoteFolder(1L, "A")),
            works = listOf(remoteWork("w1")),
            memberships = listOf(remoteMembership(1L, "w1")),
        )

        val result = merge(localFolders = emptyList(), remote = remote, snapshot = null)

        assertEquals(listOf("A"), result.folders.map { it.name })
        assertEquals(listOf("w1"), result.memberships.map { it.workId })
        assertEquals(listOf("w1"), result.works.map { it.workId })
        assertTrue(result.tombstones.isEmpty())
    }

    @Test
    fun tombstonesAreDedupedAndExpiredOnesDropped() {
        val fresh = SyncTombstone.membership("A", "w1", now - 1_000)
        val newer = SyncTombstone.membership("A", "w1", now - 500)
        val expired = SyncTombstone.membership("A", "w2", now - FavoriteSyncMerge.TOMBSTONE_TTL_MS - 1)

        val result = merge(
            remote = payload(tombstones = listOf(fresh, expired)),
            snapshot = payload(tombstones = listOf(newer)),
        )

        assertEquals("同一条删除只留最新的那次", listOf(newer), result.tombstones)
        assertTrue("过期墓碑不再随 payload 传播", result.tombstones.none { it.workId == "w2" })
    }

    @Test
    fun emptyLocalStateDoesNotWipeRemoteBackup() {
        // 本地库被重置而快照还在：不能全判成已删除，否则一次同步就清空云端备份
        val snapshot = payload(
            folders = listOf(remoteFolder(1L, "A")),
            works = listOf(remoteWork("w1")),
            memberships = listOf(remoteMembership(1L, "w1")),
        )

        val result = merge(localFolders = emptyList(), remote = snapshot, snapshot = snapshot)

        assertTrue("整份备份不能被判成删除", result.tombstones.isEmpty())
        assertEquals(listOf("A"), result.folders.map { it.name })
        assertEquals(listOf("w1"), result.memberships.map { it.workId })
        assertEquals(listOf("w1"), result.works.map { it.workId })
    }

    @Test
    fun localOnlyContentIsKeptAndUploaded() {
        // 本机新增的夹与归属：远端没有、快照也没有，必须照常上传
        val result = merge(
            localFolders = listOf(folder(1L, "A")),
            localFavorites = listOf(localWork("w1")),
            localMemberships = listOf(1L to membership(1L, "w1")),
            remote = payload(),
            snapshot = payload(),
        )

        assertEquals(listOf("A"), result.folders.map { it.name })
        assertEquals(listOf("w1"), result.memberships.map { it.workId })
        assertEquals(listOf("w1"), result.works.map { it.workId })
        assertTrue(result.tombstones.isEmpty())
    }
}

class FavoriteSyncSnapshotStoreTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    private fun tempFile(): File =
        File.createTempFile("snapshot", ".json").also { it.delete() }

    @Test
    fun roundTripsPayload() {
        val file = tempFile()
        val store = FavoriteSyncSnapshotStore(file, json)
        val data = FavoriteSyncData(
            syncedAt = 42L,
            folders = listOf(SyncFolder(1L, "A", isDefault = true, createdAt = 7L)),
            works = emptyList(),
            memberships = listOf(SyncMembership(1L, "w1", 5L)),
            tombstones = listOf(SyncTombstone.folder("B", 9L)),
        )

        assertNull("还没写过时按没有快照处理", store.read())
        store.write(data)

        assertEquals(data, FavoriteSyncSnapshotStore(file, json).read())
        file.delete()
    }

    @Test
    fun corruptFileReadsAsMissingSnapshot() {
        val file = tempFile()
        file.writeText("{ not json")

        assertNull(FavoriteSyncSnapshotStore(file, json).read())
        file.delete()
    }
}
