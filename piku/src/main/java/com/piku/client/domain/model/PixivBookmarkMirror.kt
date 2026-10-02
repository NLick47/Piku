package com.piku.client.domain.model

/**
 * pixiv 云端收藏镜像档位：收藏作品时本地必定写入，是否同时进 pixiv 个人收藏由它决定。
 * LOCAL_ONLY 是「彻底不动云端」——加收藏与取消收藏都不发任何 pixiv 请求。
 */
enum class PixivBookmarkMirror {
    /** 同步为公开收藏（默认，与历史行为一致） */
    PUBLIC,

    /** 同步为非公开收藏：进 pixiv 个人收藏但不对外可见 */
    PRIVATE,

    /** 仅收藏在本 App，不碰 pixiv */
    LOCAL_ONLY,
}
