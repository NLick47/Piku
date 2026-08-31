package com.piku.client.data.local

import com.piku.client.domain.model.Work

fun FavoriteEntity.toWork() = Work(
    id = workId.toLong(),
    authorId = authorId,
    authorName = authorName,
    authorAvatarUrl = authorAvatarUrl,
    categoryCd = -1,
    categoryName = "",
    title = title,
    thumbnailUrl = thumbnailUrl,
    imageCount = imageCount,
    r18 = r18,
)

fun HistoryEntity.toWork() = Work(
    id = workId.toLong(),
    authorId = authorId,
    authorName = authorName,
    authorAvatarUrl = authorAvatarUrl,
    categoryCd = -1,
    categoryName = "",
    title = title,
    thumbnailUrl = thumbnailUrl,
    imageCount = imageCount,
    r18 = r18,
)

fun Work.toFavoriteEntity(addedAt: Long = System.currentTimeMillis()) = FavoriteEntity(
    workId = id.toString(),
    authorId = authorId,
    title = title,
    authorName = authorName,
    thumbnailUrl = thumbnailUrl,
    authorAvatarUrl = authorAvatarUrl,
    imageCount = imageCount,
    r18 = r18,
    addedAt = addedAt,
)

fun FavoriteEntity.toSyncWork(): com.piku.client.domain.model.SyncWork =
    com.piku.client.domain.model.SyncWork(
        workId = workId,
        authorId = authorId,
        title = title,
        authorName = authorName,
        thumbnailUrl = thumbnailUrl,
        authorAvatarUrl = authorAvatarUrl,
        imageCount = imageCount,
        r18 = r18,
        addedAt = addedAt,
        contentBackedUp = contentBackedUp,
    )