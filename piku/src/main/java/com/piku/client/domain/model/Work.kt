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
    val imageCount: Int,
    val r18: Boolean,
    val warning: Boolean = false,
    val loginRequired: Boolean = false,
)