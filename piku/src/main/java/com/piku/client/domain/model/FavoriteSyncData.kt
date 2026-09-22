package com.piku.client.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FavoriteSyncData(
    val version: Int = 1,
    val syncedAt: Long,
    val folders: List<SyncFolder>,
    val works: List<SyncWork>,
    val memberships: List<SyncMembership>,
    val tombstones: List<SyncTombstone> = emptyList(),
)

@Serializable
data class SyncTombstone(
    val kind: String,
    val folderName: String,
    val workId: String = "",
    val deletedAt: Long,
) {
    val key: String
        get() = if (kind == KIND_MEMBERSHIP) {
            "$kind\u0000$folderName\u0000$workId"
        } else {
            "$kind\u0000$folderName"
        }

    companion object {
        const val KIND_FOLDER = "FOLDER"
        const val KIND_MEMBERSHIP = "MEMBERSHIP"

        fun folder(name: String, deletedAt: Long) =
            SyncTombstone(KIND_FOLDER, name, deletedAt = deletedAt)

        fun membership(folderName: String, workId: String, deletedAt: Long) =
            SyncTombstone(KIND_MEMBERSHIP, folderName, workId, deletedAt)
    }
}

@Serializable
data class SyncFolder(
    val id: Long,
    val name: String,
    val isDefault: Boolean,
    val createdAt: Long,
)

@Serializable
data class SyncWork(
    val workId: String,
    val authorId: Long,
    val title: String,
    val authorName: String,
    val thumbnailUrl: String,
    val authorAvatarUrl: String?,
    val imageCount: Int,
    val r18: Boolean,
    val addedAt: Long,
    /** 该作品的内容是否已备份到 WebDAV */
    val contentBackedUp: Boolean = false,
)

@Serializable
data class SyncMembership(
    val folderId: Long,
    val workId: String,
    val addedAt: Long,
)
