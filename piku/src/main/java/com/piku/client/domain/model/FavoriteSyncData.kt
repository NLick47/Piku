package com.piku.client.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FavoriteSyncData(
    val version: Int = 1,
    val syncedAt: Long,
    val folders: List<SyncFolder>,
    val works: List<SyncWork>,
    val memberships: List<SyncMembership>,
)

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
