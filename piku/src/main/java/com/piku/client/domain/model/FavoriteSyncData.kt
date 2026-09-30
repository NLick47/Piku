package com.piku.client.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FavoriteSyncData(
    /** 协议版本，写在云端文件里；[CURRENT_VERSION] 之外的版本不合并 */
    val version: Int,
    val syncedAt: Long,
    val folders: List<SyncFolder>,
    val works: List<SyncWork>,
    val memberships: List<SyncMembership>,
    val tombstones: List<SyncTombstone> = emptyList(),
) {
    companion object {
        /**
         * 协议版本。2 起 source 是必填字段，1 及更早的云端文件不再兼容，
         * 同步时按"首次同步"处理（云端那份会被本机覆盖）。
         */
        const val CURRENT_VERSION = 2
    }
}

@Serializable
data class SyncTombstone(
    val kind: String,
    val folderName: String,
    /** 只有 MEMBERSHIP 墓碑有源；FOLDER 墓碑不带 */
    val source: String? = null,
    val workId: String = "",
    val deletedAt: Long,
) {
    val key: String
        get() = if (kind == KIND_MEMBERSHIP) {
            "$kind\u0000$folderName\u0000$source\u0000$workId"
        } else {
            "$kind\u0000$folderName"
        }

    /** 未知源（更新版本客户端写入的）给 null：本地无从删除；FOLDER 墓碑也没有源 */
    val workSource: WorkSource?
        get() = WorkSource.entries.firstOrNull { it.name == source }

    companion object {
        const val KIND_FOLDER = "FOLDER"
        const val KIND_MEMBERSHIP = "MEMBERSHIP"

        fun folder(name: String, deletedAt: Long) =
            SyncTombstone(KIND_FOLDER, name, deletedAt = deletedAt)

        fun membership(folderName: String, source: WorkSource, workId: String, deletedAt: Long) =
            SyncTombstone(KIND_MEMBERSHIP, folderName, source.name, workId, deletedAt)
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
    val source: String,
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
) {
    /** 未知源（更新版本客户端写入的）给 null：本地写不进去，但云端保留原样 */
    val workSource: WorkSource?
        get() = WorkSource.entries.firstOrNull { it.name == source }
}

@Serializable
data class SyncMembership(
    val folderId: Long,
    val source: String,
    val workId: String,
    val addedAt: Long,
) {
    /** 未知源（更新版本客户端写入的）给 null：本地写不进去，但云端保留原样 */
    val workSource: WorkSource?
        get() = WorkSource.entries.firstOrNull { it.name == source }
}
