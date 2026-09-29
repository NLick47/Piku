package com.piku.client.domain.source

import androidx.annotation.StringRes
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource

interface ContentSource {

    val id: WorkSource

    /** 源显示名，换源控件用 */
    @get:StringRes val labelRes: Int

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

    /** 应用内看图的作品页列表；只有声明 [SourceWorkOpen.InAppViewer] 的源会被调到 */
    suspend fun workPages(work: Work): Result<List<SourceWorkPage>>

    /** 详情页要展示的补充文本（简介/标签）；null = 该源没有，详情壳不显示这两块 */
    suspend fun workDetailText(work: Work): Result<SourceWorkText?> = Result.success(null)
}

/** 详情页的源补充文本 */
data class SourceWorkText(
    val description: String = "",
    val tags: List<String> = emptyList(),
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
    data object InAppViewer : SourceWorkOpen
    data class External(val url: String) : SourceWorkOpen
}

data class SourceFeed(
    val id: String,
    @StringRes val labelRes: Int,
    val paginated: Boolean = true,
    val requiresLogin: Boolean = false,
    val chronological: Boolean = true,
    /** 条目按名次排列：外壳给前三名 hero 位，其余在卡片上挂名次角标 */
    val ranked: Boolean = false,
    /** 占位流：能力未到（如 pixiv 登录后的推荐），壳显示"即将上线"且不发请求 */
    val comingSoon: Boolean = false,
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
)

/** 组的展示形态：常显片选（高频切换）或 tab 行尾下拉（低频筛选） */
enum class SourceFacetStyle { Chips, Dropdown }
