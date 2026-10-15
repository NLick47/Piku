package com.piku.client.core.model

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
    /** 小说字数；插画恒 0，卡片据此显示篇幅 */
    val textLength: Int = 0,
    val r18: Boolean,
    val ai: Boolean = false,
    val warning: Boolean = false,
    val loginRequired: Boolean = false,
    val isPrivate: Boolean = false,
    val source: WorkSource = WorkSource.POIPIKU,
    /** 小说与插画在 pixiv 是两套 id 序列，卡片与详情按类型分流 */
    val kind: WorkKind = WorkKind.ILLUST,
    /** 连载中（AO3 的 Work in Progress）；false = 完结或该源不给这个信息 */
    val inProgress: Boolean = false,
    /** 梗概：列表卡上要展示的一段话（AO3 的 summary）。空 = 该源/该链路没给，卡片不占这块 */
    val summary: String = "",
    /** 列表卡展示的标签（AO3：关系/角色/自由标签，按站点顺序）；空 = 不显示 */
    val tags: List<String> = emptyList(),
    /** 点赞数（AO3 的 kudos）；0 = 该源不给或确实为零，卡片不显示 */
    val likes: Int = 0,
)