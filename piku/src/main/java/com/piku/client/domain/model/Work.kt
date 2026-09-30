package com.piku.client.domain.model

data class Work(
    val id: Long,
    val authorId: Long,
    val authorName: String,
    val authorAvatarUrl: String?,
    val categoryCd: Int,
    val categoryName: String,
    val title: String,
    val thumbnailUrl: String,
    /** 缩略图对应的原作宽高：非裁切卡片按它排版。0 = 该源没给，卡片退回方图 */
    val thumbWidth: Int = 0,
    val thumbHeight: Int = 0,
    val imageCount: Int,
    val r18: Boolean,
    val warning: Boolean = false,
    val loginRequired: Boolean = false,
    val isPrivate: Boolean = false,
    val source: WorkSource = WorkSource.POIPIKU,
)