package com.piku.client.domain.model

data class WorkStats(
    /** 浏览数 */
    val views: Int = 0,
    /**
     * 点赞数（pixiv 的「いいね」，仅展示、客户端不做点赞操作）。
     * null = 该链路不提供（app-api 不带点赞数）：UI 不渲染该格，不当作 0。
     */
    val likes: Int? = null,
    /** 收藏数 */
    val bookmarks: Int = 0,
    /** 投稿时间（接口原始 rfc3339 串，展示只取日期段） */
    val postedAt: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val pageCount: Int = 0,
    /** 作者账号（@xxx），展示在作者名下 */
    val authorAccount: String = "",
) {
    /** 计数接口偶发整体缺失（匿名限制、作品被限），全 0 时 UI 不展示数据条 */
    val hasCounts: Boolean get() = views > 0 || (likes ?: 0) > 0 || bookmarks > 0
}
