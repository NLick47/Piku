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

    /** 第二维：在流之上收窄（poipiku 的分类、pixiv 的内容类型）。空 = 该源没有这一维 */
    val facets: List<SourceFacet>

    /** 取一页。[feedId]/[facetId] 都是本源声明过的 id，外壳只负责透传 */
    suspend fun page(feedId: String, facetId: String?, page: Int): Result<SourcePage>

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
    val url: String,
    val fullUrl: String = "",
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
)

data class SourceFacet(
    val id: String,
    @StringRes val labelRes: Int,
    val selectedByDefault: Boolean = false,
)
