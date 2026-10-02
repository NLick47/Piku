package com.piku.client.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.piku.client.domain.model.WorkSource

/** source 与 workId 组成跨源唯一键；存量数据迁移时一律落 POIPIKU */
@Entity(tableName = "favorites", primaryKeys = ["source", "workId"], indices = [Index("addedAt")])
data class FavoriteEntity(
    val source: WorkSource = WorkSource.POIPIKU,
    val workId: String,
    val authorId: Long,
    val title: String,
    val authorName: String,
    @ColumnInfo(defaultValue = "") val thumbnailUrl: String,
    val authorAvatarUrl: String?,
    @ColumnInfo(defaultValue = "0") val imageCount: Int,
    @ColumnInfo(defaultValue = "0") val r18: Boolean,
    val addedAt: Long,
    @ColumnInfo(defaultValue = "0") val contentBackedUp: Boolean = false,
    @ColumnInfo(defaultValue = "0") val cloudSynced: Boolean = false,
)
