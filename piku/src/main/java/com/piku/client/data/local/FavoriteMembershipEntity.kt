package com.piku.client.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "favorite_memberships",
    primaryKeys = ["folderId", "workId"],
    foreignKeys = [
        ForeignKey(
            entity = FavoriteFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FavoriteEntity::class,
            parentColumns = ["workId"],
            childColumns = ["workId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("folderId"), Index("workId")],
)
data class FavoriteMembershipEntity(
    val folderId: Long,
    val workId: String,
    val addedAt: Long,
)
