package com.piku.client.core.source

import com.piku.client.core.model.WorkSource

/** 图文源共用的资料库池：poipiku 与 pixiv 的收藏/历史混排成一池 */
const val LIBRARY_POOL_MEDIA = "media"

/**
 * 一个资料库池。同池的源在收藏/历史里混排，异池彼此不可见。
 * [singleColumn] = 池里全是纯文字源（如 AO3）→ 列表走单列 blurb。
 */
data class LibraryPool(
    val sources: Set<WorkSource>,
    val singleColumn: Boolean,
)
