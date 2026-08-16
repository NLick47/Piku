package com.piku.client.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val workId: String,
    val authorId: Long,
    val title: String,
    val authorName: String,
    @ColumnInfo(defaultValue = "") val thumbnailUrl: String,
    val authorAvatarUrl: String?,
    @ColumnInfo(defaultValue = "0") val imageCount: Int,
    @ColumnInfo(defaultValue = "0") val r18: Boolean,
    val addedAt: Long,
)