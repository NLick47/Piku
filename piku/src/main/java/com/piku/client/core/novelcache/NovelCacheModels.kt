package com.piku.client.core.novelcache

import com.piku.client.core.model.NovelChapter
import com.piku.client.core.model.WorkKey

/** 缓存里的一篇正文；章节顺序即阅读顺序 */
data class CachedNovel(
    val key: WorkKey,
    val title: String,
    val authorName: String,
    val chapters: List<NovelChapter>,
    val fetchedAt: Long,
    val byteSize: Long,
)

/** 缓存管理页用的轻量投影：不带正文 */
data class NovelCacheSummary(
    val key: WorkKey,
    val title: String,
    val authorName: String,
    val authorId: Long,
    val chapterCount: Int,
    val byteSize: Long,
    val fetchedAt: Long,
)

/** 详情页缓存按钮要展示的状态投影 */
data class NovelCacheSnapshot(
    val chapterCount: Int,
    val byteSize: Long,
    val fetchedAt: Long,
    /** 远端比本地多出来的章数；0 = 没有更新或不知道 */
    val updateCount: Int,
)

/** 读正文的结果：有缓存就直接读缓存，没有才走网络 */
data class NovelRead(
    val chapters: List<NovelChapter>,
    val fromCache: Boolean,
    /** 取网失败后回落的缓存：只有这种情况需要提示用户在看离线副本 */
    val fallback: Boolean = false,
)
