package com.piku.client.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val workId: String,
    val authorId: Long,
    val title: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val thumbnailUrl: String,
    val imageCount: Int,
    val r18: Boolean,
    val visitedAt: Long,
)