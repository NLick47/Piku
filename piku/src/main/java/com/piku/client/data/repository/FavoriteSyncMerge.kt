package com.piku.client.data.repository

import com.piku.client.data.local.FavoriteEntity
import com.piku.client.data.local.FavoriteFolderEntity
import com.piku.client.data.local.FavoriteMembershipEntity
import com.piku.client.data.local.toSyncWork
import com.piku.client.domain.model.FavoriteSyncData
import com.piku.client.domain.model.SyncFolder
import com.piku.client.domain.model.SyncMembership
import com.piku.client.domain.model.SyncTombstone
import com.piku.client.domain.model.SyncWork
import kotlinx.serialization.json.Json
import java.io.File

/** 合并规则：并集之外还要按墓碑剔除已删除的条目（墓碑 = 上次同步快照与本地状态的差集） */
internal object FavoriteSyncMerge {

    const val TOMBSTONE_TTL_MS: Long = 90L * 24 * 60 * 60 * 1000

    fun membershipKey(folderName: String, workId: String): String = "$folderName\u0000$workId"

    fun merge(
        localFolders: List<FavoriteFolderEntity>,
        localFavorites: List<FavoriteEntity>,
        localMemberships: List<Pair<Long, FavoriteMembershipEntity>>,
        remote: FavoriteSyncData?,
        snapshot: FavoriteSyncData?,
        now: Long,
    ): FavoriteSyncData {
        val tombstones = mergeTombstones(
            snapshot?.tombstones.orEmpty() + remote?.tombstones.orEmpty(),
            now,
        ) + deriveTombstones(localFolders, localMemberships, snapshot, now)
        val mergedTombstones = mergeTombstones(tombstones, now)

        val deadFolders = mergedTombstones
            .filter { it.kind == SyncTombstone.KIND_FOLDER }
            .associate { it.folderName to it.deletedAt }
        val deadMemberships = mergedTombstones
            .filter { it.kind == SyncTombstone.KIND_MEMBERSHIP }
            .associate { membershipKey(it.folderName, it.workId) to it.deletedAt }

        val mergedFolders = mergeFolders(localFolders, remote?.folders.orEmpty(), deadFolders, now)
        val mergedMemberships = mergeMemberships(
            localFolders = localFolders,
            localMemberships = localMemberships,
            remote = remote,
            folderIdByName = mergedFolders.associate { it.name to it.id },
            deadMemberships = deadMemberships,
        )
        val mergedWorks = mergeWorks(
            localFavorites = localFavorites,
            remote = remote?.works.orEmpty(),
            // 失去全部归属的作品不再上传（例如只属于被删掉的那个夹）
            liveWorkIds = mergedMemberships.mapTo(mutableSetOf()) { it.workId },
        )

        return FavoriteSyncData(
            syncedAt = now,
            folders = mergedFolders,
            works = mergedWorks,
            memberships = mergedMemberships,
            tombstones = mergedTombstones,
        )
    }

    /** 快照里有、本地没有的条目，就是这台设备删掉的 */
    private fun deriveTombstones(
        localFolders: List<FavoriteFolderEntity>,
        localMemberships: List<Pair<Long, FavoriteMembershipEntity>>,
        snapshot: FavoriteSyncData?,
        now: Long,
    ): List<SyncTombstone> {
        if (snapshot == null) return emptyList()
        // 本地夹表空而快照里还有：只能是库被重置（运行中的 App 至少有一个默认夹）。
        // 此时推导删除会清空云端备份，宁可不推导。
        if (localFolders.isEmpty() && snapshot.folders.isNotEmpty()) return emptyList()
        val localFolderNames = localFolders.mapTo(mutableSetOf()) { it.name }
        val localFolderNameById = localFolders.associate { it.id to it.name }
        val localMembershipKeys = localMemberships.mapNotNull { (folderId, membership) ->
            localFolderNameById[folderId]?.let { membershipKey(it, membership.workId) }
        }.toSet()
        val snapshotFolderNameById = snapshot.folders.associate { it.id to it.name }

        val folders = snapshot.folders
            .filter { it.name !in localFolderNames }
            .map { SyncTombstone.folder(it.name, now) }
        val deadNames = folders.mapTo(mutableSetOf()) { it.folderName }
        val memberships = snapshot.memberships.mapNotNull { membership ->
            val folderName = snapshotFolderNameById[membership.folderId] ?: return@mapNotNull null
            if (folderName in deadNames) return@mapNotNull null
            if (membershipKey(folderName, membership.workId) in localMembershipKeys) return@mapNotNull null
            SyncTombstone.membership(folderName, membership.workId, now)
        }
        return folders + memberships
    }

    /** 去重（同键取最新的 deletedAt）并清掉过期记录 */
    private fun mergeTombstones(all: List<SyncTombstone>, now: Long): List<SyncTombstone> {
        val expireBefore = now - TOMBSTONE_TTL_MS
        return all.filter { it.deletedAt >= expireBefore }
            .groupBy { it.key }
            .map { (_, sameTarget) -> sameTarget.maxBy { it.deletedAt } }
    }

    private fun mergeFolders(
        local: List<FavoriteFolderEntity>,
        remote: List<SyncFolder>,
        deadFolders: Map<String, Long>,
        now: Long,
    ): List<SyncFolder> {
        val localByName = local.associateBy { it.name }
        val remoteByName = remote.associateBy { it.name }
        return (localByName.keys + remoteByName.keys).mapNotNull { name ->
            val localFolder = localByName[name]
            val remoteFolder = remoteByName[name]
            val createdAt = minOf(
                localFolder?.createdAt ?: Long.MAX_VALUE,
                remoteFolder?.createdAt ?: Long.MAX_VALUE,
            ).let { if (it == Long.MAX_VALUE) now else it }
            // 删除优先；改名也走这里：旧名成墓碑，新名照常合并
            if (deadFolders[name]?.let { createdAt <= it } == true) return@mapNotNull null
            SyncFolder(
                id = remoteFolder?.id ?: localFolder?.id ?: now,
                name = name,
                isDefault = localFolder?.isDefault == true || remoteFolder?.isDefault == true,
                createdAt = createdAt,
            )
        }
    }

    private fun mergeMemberships(
        localFolders: List<FavoriteFolderEntity>,
        localMemberships: List<Pair<Long, FavoriteMembershipEntity>>,
        remote: FavoriteSyncData?,
        folderIdByName: Map<String, Long>,
        deadMemberships: Map<String, Long>,
    ): List<SyncMembership> {
        val merged = mutableListOf<SyncMembership>()
        val seen = mutableSetOf<String>()
        val localFolderNameById = localFolders.associate { it.id to it.name }
        localMemberships.forEach { (folderId, membership) ->
            val folderName = localFolderNameById[folderId] ?: return@forEach
            val key = membershipKey(folderName, membership.workId)
            val mergedFolderId = folderIdByName[folderName] ?: return@forEach
            if (!seen.add(key)) return@forEach
            if (deadMemberships[key]?.let { membership.addedAt <= it } == true) return@forEach
            merged += SyncMembership(mergedFolderId, membership.workId, membership.addedAt)
        }
        // 本地已有同 (夹名, workId) 时以本地为准
        val remoteFolderNameById = remote?.folders.orEmpty().associate { it.id to it.name }
        remote?.memberships.orEmpty().forEach { membership ->
            val folderName = remoteFolderNameById[membership.folderId] ?: return@forEach
            val key = membershipKey(folderName, membership.workId)
            val mergedFolderId = folderIdByName[folderName] ?: return@forEach
            if (!seen.add(key)) return@forEach
            if (deadMemberships[key]?.let { membership.addedAt <= it } == true) return@forEach
            merged += SyncMembership(mergedFolderId, membership.workId, membership.addedAt)
        }
        return merged
    }

    private fun mergeWorks(
        localFavorites: List<FavoriteEntity>,
        remote: List<SyncWork>,
        liveWorkIds: Set<String>,
    ): List<SyncWork> {
        val remoteById = remote.associateBy { it.workId }
        val localById = localFavorites.associateBy { it.workId }
        return (localById.keys + remoteById.keys).mapNotNull { workId ->
            if (workId !in liveWorkIds) return@mapNotNull null
            val localWork = localById[workId]
            val remoteWork = remoteById[workId]
            if (localWork == null) {
                remoteWork
            } else {
                localWork.toSyncWork().copy(
                    // 两端都收藏过时取更晚的时间
                    addedAt = maxOf(localWork.addedAt, remoteWork?.addedAt ?: 0L),
                    contentBackedUp = localWork.contentBackedUp || (remoteWork?.contentBackedUp ?: false),
                )
            }
        }
    }
}

internal class FavoriteSyncSnapshotStore(
    private val file: File,
    private val json: Json,
) {
    fun read(): FavoriteSyncData? = runCatching {
        if (!file.exists()) return@runCatching null
        json.decodeFromString<FavoriteSyncData>(file.readText())
    }.getOrNull()

    fun write(data: FavoriteSyncData) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(data))
        }
    }
}
