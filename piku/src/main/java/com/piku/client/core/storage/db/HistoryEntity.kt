package com.piku.client.core.storage.db

import androidx.room.Entity
import androidx.room.Index
import com.piku.client.core.model.WorkSource

/** source 与 workId 组成跨源唯一键；存量数据迁移时一律落 POIPIKU */
@Entity(tableName = "history", primaryKeys = ["source", "workId"], indices = [Index("visitedAt")])
data class HistoryEntity(
    val source: WorkSource = WorkSource.POIPIKU,
    val workId: String,
    val authorId: Long,
    val title: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val thumbnailUrl: String,
    val imageCount: Int,
    val r18: Boolean,
    val visitedAt: Long,
)
