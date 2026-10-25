package com.piku.client.core.novelcache

import com.piku.client.core.model.AppError
import com.piku.client.core.model.NovelChapter
import com.piku.client.core.model.Work
import com.piku.client.core.source.ContentSource

/** 分章源返回章节；单篇源连同 novelBody 包成一条。详情页与缓存页共用一个取文形状 */
suspend fun fetchNovelChapters(
    source: ContentSource,
    work: Work,
): Result<List<NovelChapter>> {
    val chapters = source.novelChapters(work).getOrNull()
    if (!chapters.isNullOrEmpty()) return Result.success(chapters)
    val body = source.novelBody(work).getOrNull()
    if (body.isNullOrBlank()) return Result.failure(AppError.NotFound)
    return Result.success(listOf(NovelChapter(title = "", text = body)))
}
