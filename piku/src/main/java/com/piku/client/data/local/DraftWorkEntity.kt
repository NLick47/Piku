package com.piku.client.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 发布草稿（单槽位）。payload 为 [com.piku.client.domain.model.PublishDraft] 的 JSON；
 * 图片已提前拷贝进 filesDir/drafts/，payload 里只存绝对路径。任何时候最多一行。
 */
@Entity(tableName = "draft_works")
data class DraftWorkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payload: String,
    val updatedAt: Long,
)
