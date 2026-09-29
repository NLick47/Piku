package com.piku.client.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.piku.client.domain.model.WorkSource

/**
 * (folderId, source, workId) 唯一；对 favorites 的外键落在复合主键上，
 * 删除作品行时连带删除归属（CASCADE）。
 */
@Entity(
    tableName = "favorite_memberships",
    primaryKeys = ["folderId", "source", "workId"],
    foreignKeys = [
        ForeignKey(
            entity = FavoriteFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FavoriteEntity::class,
            parentColumns = ["source", "workId"],
            childColumns = ["source", "workId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("folderId"), Index("source", "workId")],
)
data class FavoriteMembershipEntity(
    val folderId: Long,
    val source: WorkSource = WorkSource.POIPIKU,
    val workId: String,
    val addedAt: Long,
)
