package com.piku.client.domain.source

import androidx.annotation.StringRes
import com.piku.client.R
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.model.WorkStats

interface ContentSource {

    val id: WorkSource

    /** 源显示名，换源控件用 */
    @get:StringRes val labelRes: Int

    /** 登录门文案：requiresLogin 流未登录时门屏显示。默认沿用 poipiku 关注门的老文案，pixiv 覆写点名自家账号 */
    @get:StringRes val loginPromptRes: Int
        get() = R.string.home_follow_login

    /** 第一维：流。外壳按声明顺序渲染成 tab 行 */
    val feeds: List<SourceFeed>

    /** 第二维：流之上的收窄（pixiv 的周期/内容类型、poipiku 的分类）。空 = 该源没有这一维 */
    val facets: List<SourceFacetGroup>

    /** 取一页。[facets] 是各维度组的当前选项 id，外壳只负责透传 */
    suspend fun page(feedId: String, facets: Map<String, String>, page: Int): Result<SourcePage>

    /** 按 id 取流声明。行为标志（分页/登录/时间序）只声明一份，引擎与 UI 都从这里拿 */
    fun feed(feedId: String): SourceFeed = feeds.first { it.id == feedId }

    /** 点开一个作品的去向，外壳只执行不解释 */
    fun open(work: Work): SourceWorkOpen

    /**
     * 点作者的去向；null = 本源在 App 里没有作者页，外壳把作者区做成不可点。
     * 不声明就只能按别的源的作者页开：拿本源的作者 id 去查另一站，看到的是别人的作品。
     */
    fun authorPage(work: Work): SourceAuthorOpen? = null

    /** 应用内看图的作品页列表；只有声明 [SourceWorkOpen.InAppViewer] 的源会被调到 */
    suspend fun workPages(work: Work): Result<List<SourceWorkPage>>

    /** 详情页要展示的补充文本（简介/标签）；null = 该源没有，详情壳不显示这两块 */
    suspend fun workDetailText(work: Work): Result<SourceWorkText?> = Result.success(null)

    /**
     * 详情页底部的相关作品。默认没有（poipiku 的相关投稿走它自己那条链路）；
     * 这一路是异步的，取不到就整块不显示，不影响详情本身。
     */
    suspend fun relatedWorks(work: Work): Result<List<Work>> = Result.success(emptyList())
}

/** 详情页的源补充文本 */
data class SourceWorkText(
    val description: String = "",
    val tags: List<String> = emptyList(),
    /**
     * 计数与元信息；只有 pixiv 这类接口给得出来的源会填，
     * poipiku 的作品页没有这些数据，恒为 null（详情 UI 据此跳过整块）。
     */
    val stats: WorkStats? = null,
)

data class SourceWorkPage(
    /** 首屏打底：能立刻显示的那档（pixiv 给 540px small，几十 KB） */
    val url: String,
    /** 清晰档：查看器稳定后显示、图片翻译与分享取它（pixiv 给 master1200，约 1 MB） */
    val fullUrl: String = "",
    /** 原图：只有保存原图才取（pixiv 给 img-original，几 MB）；不填表示与 [fullUrl] 同档 */
    val originalUrl: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

sealed interface SourceWorkOpen {
    /**
     * 主壳的专属详情页承载这一源的作品（poipiku 的登录门/R-18 门/密码门/小说阅读器都在里面）。
     * 外壳据此把点击交给自家详情路由，不去碰 [External] 那个兜底地址。
     */
    data object NativeDetail : SourceWorkOpen

    data object InAppViewer : SourceWorkOpen
    data class External(val url: String) : SourceWorkOpen
}

/** 作者的页面的去向：只有「主壳自己的作者页」和「出站到源的作者页」两种，点作者没有第三方可能 */
sealed interface SourceAuthorOpen {
    /** 主壳的「用户作品」页承载这一源的作者 */
    data object NativeDetail : SourceAuthorOpen

    data class External(val url: String) : SourceAuthorOpen
}

data class SourceFeed(
    val id: String,
    @StringRes val labelRes: Int,
    val paginated: Boolean = true,
    val requiresLogin: Boolean = false,
    val chronological: Boolean = true,
    /** 条目按名次排列：外壳给前三名 hero 位，其余在卡片上挂名次角标 */
    val ranked: Boolean = false,
    /** 条目带原作宽高：外壳按原图比例排版卡片，而不是裁成方图 */
    val proportional: Boolean = false,
    /** 占位流：能力未到（如 pixiv 登录后的推荐），壳显示"即将上线"且不发请求 */
    val comingSoon: Boolean = false,
    val pendingAfterLogin: Boolean = false,
)

data class SourceFacet(
    val id: String,
    @StringRes val labelRes: Int,
    val selectedByDefault: Boolean = false,
    /** 菜单项里的补充说明（如榜单的更新节奏）；null = 不显示 */
    @StringRes val hintRes: Int? = null,
)

/** 一组收窄维度。一个源可声明多组（pixiv：周期=片选 + 内容类型=下拉） */
data class SourceFacetGroup(
    val id: String,
    val style: SourceFacetStyle,
    val options: List<SourceFacet>,
    /** 只在指定流显示（pixiv 的周期/内容只属于榜单）；null = 该源所有流共用 */
    val feedId: String? = null,
)

/** 组的展示形态：常显片选（高频切换）或 tab 行尾下拉（低频筛选） */
enum class SourceFacetStyle { Chips, Dropdown }
