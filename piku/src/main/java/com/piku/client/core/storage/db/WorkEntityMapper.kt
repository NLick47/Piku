package com.piku.client.core.storage.db

import com.piku.client.core.model.Work
import com.piku.client.core.model.decodeWorkId

fun FavoriteEntity.toWork(): Work {
    val (id, kind) = decodeWorkId(workId)
    return Work(
        id = id,
        authorId = authorId,
        authorName = authorName,
        authorAvatarUrl = authorAvatarUrl,
        categoryCd = -1,
        categoryName = "",
        title = title,
        thumbnailUrl = thumbnailUrl,
        imageCount = imageCount,
        r18 = r18,
        source = source,
        kind = kind,
    )
}

fun HistoryEntity.toWork(): Work {
    val (id, kind) = decodeWorkId(workId)
    return Work(
        id = id,
        authorId = authorId,
        authorName = authorName,
        authorAvatarUrl = authorAvatarUrl,
        categoryCd = -1,
        categoryName = "",
        title = title,
        thumbnailUrl = thumbnailUrl,
        imageCount = imageCount,
        r18 = r18,
        source = source,
        kind = kind,
    )
}

fun Work.toFavoriteEntity(addedAt: Long = System.currentTimeMillis()) = FavoriteEntity(
    source = source,
    workId = kind.encode(id),
    authorId = authorId,
    title = title,
    authorName = authorName,
    thumbnailUrl = thumbnailUrl,
    authorAvatarUrl = authorAvatarUrl,
    imageCount = imageCount,
    r18 = r18,
    addedAt = addedAt,
)

fun Work.toHistoryEntity(visitedAt: Long = System.currentTimeMillis()) = HistoryEntity(
    source = source,
    workId = kind.encode(id),
    authorId = authorId,
    title = title,
    authorName = authorName,
    thumbnailUrl = thumbnailUrl,
    authorAvatarUrl = authorAvatarUrl,
    imageCount = imageCount,
    r18 = r18,
    visitedAt = visitedAt,
)

fun FavoriteEntity.toSyncWork(): com.piku.client.core.model.SyncWork =
    com.piku.client.core.model.SyncWork(
        source = source.name,
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
