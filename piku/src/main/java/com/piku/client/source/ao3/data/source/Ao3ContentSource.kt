package com.piku.client.source.ao3.data.source

import com.piku.client.R
import com.piku.client.core.model.AppError
import com.piku.client.core.model.NovelChapter
import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkSource
import com.piku.client.core.model.WorkStats
import com.piku.client.core.source.AuthorPageStyle
import com.piku.client.core.source.ContentSource
import com.piku.client.core.source.SourceAuthorOpen
import com.piku.client.core.source.SourceFacet
import com.piku.client.core.source.SourceFacetGroup
import com.piku.client.core.source.SourceFacetStyle
import com.piku.client.core.source.SourceFeed
import com.piku.client.core.source.SourcePage
import com.piku.client.core.source.SourceScope
import com.piku.client.core.source.SourceWorkOpen
import com.piku.client.core.source.SourceWorkPage
import com.piku.client.core.source.SourceWorkText
import com.piku.client.source.ao3.data.remote.Ao3FacetParams
import com.piku.client.source.ao3.data.remote.Ao3ScopeParams
import com.piku.client.source.ao3.data.repository.Ao3Repository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Ao3ContentSource @Inject constructor(
    private val repository: Ao3Repository,
) : ContentSource {

    override val id = WorkSource.AO3

    override val labelRes = R.string.home_source_ao3

    override val feeds = FEEDS

    /** 纯文字站：单列 blurb，双列塞不下简介与标签 */
    override val listLayout = com.piku.client.core.source.SourceListLayout.List

    /** AO3 本身对访客全开放（Explicit 也直接可读），卡片上的 R-18 徽标已足够 */
    override val gatesAdultContent = false

    /** 排序与筛选收进列表首行的摘要控件：圈层式首页把纵向空间让给作品 */
    override val compactControls = true

    /** 圈层内只保留最硬的收窄需求：不追坑。字数卡片上直接可见，分级有徽标，都不占位置 */
    override val facets = listOf(
        SourceFacetGroup(
            id = FACET_COMPLETE,
            style = SourceFacetStyle.Chips,
            labelRes = R.string.ao3_facet_complete,
            options = listOf(
                SourceFacet(id = COMPLETE_ALL, labelRes = R.string.ao3_complete_all, selectedByDefault = true),
                SourceFacet(id = COMPLETE_YES, labelRes = R.string.ao3_complete_yes),
                SourceFacet(id = COMPLETE_NO, labelRes = R.string.ao3_complete_no),
            ),
        ),
    )

    override suspend fun page(
        feedId: String,
        facets: Map<String, String>,
        page: Int,
        scope: SourceScope?,
    ): Result<SourcePage> {
        val sort = SORT_COLUMNS[feedId] ?: return Result.failure(AppError.NotFound)
        return repository.listing(
            query = null,
            tag = null,
            user = null,
            sortColumn = sort,
            // 外壳的页码从 0 起，AO3 从 1 起
            page = page + 1,
            // 圈层收窄与维度收窄落在同一组 work_search 参数里，合并后一次发给搜索端点
            extra = Ao3ScopeParams.of(scope) + Ao3FacetParams.of(facets),
        )
            .map { (items, hasNext) ->
                // 末页靠有无 rel="next" 判定：AO3 的 page 从 1 起、不给总数
                SourcePage(items = items, totalPages = if (hasNext) null else page)
            }
    }

    override fun open(work: Work): SourceWorkOpen = SourceWorkOpen.InAppViewer

    /**
     * 作者区不可点。AO3 的作者页 URL 要用 URL 里的 username（`/users/{user}/pseuds/{pseud}`），
     * 而 Work 上只有展示用的笔名——笔名与 username 可以不同，拿笔名拼 URL 会指向别人的
     * 页面（甚至 404）。要能点需要在 Work 上带 username 字段，后续再扩。
     */
    override fun authorPage(work: Work): SourceAuthorOpen? = null

    override val authorPageStyle: AuthorPageStyle = AuthorPageStyle.Works

    /** AO3 是纯文字站：没有页表。空列表让外壳走空态，不是失败 */
    override suspend fun workPages(work: Work): Result<List<SourceWorkPage>> =
        Result.success(emptyList())

    /** 详情页：简介/标签/统计/状态都从作品页元数据来（首章页即可，不带全文） */
    override suspend fun workDetailText(work: Work): Result<SourceWorkText?> =
        repository.workDetail(work.id).map { detail ->
            SourceWorkText(
                title = detail.title,
                authorName = detail.authorName,
                description = detail.summary,
                tags = buildList {
                    // 按 AO3 作品页自己的展示顺序铺标签流：分级/警告/分类/同人/关系/角色/自由标签
                    if (detail.rating.isNotBlank()) add(detail.rating)
                    addAll(detail.warnings)
                    addAll(detail.categories)
                    addAll(detail.fandoms)
                    addAll(detail.tags)
                },
                stats = WorkStats(
                    views = detail.hits,
                    likes = detail.kudos,
                    bookmarks = detail.bookmarks,
                    postedAt = detail.publishedAt,
                    // 章数走 chapters：pageCount 在详情里按"张"（插画页数）渲染
                    chapters = detail.postedChapters,
                ),
                statusRes = when {
                    detail.postedChapters <= 0 -> 0
                    detail.complete -> R.string.work_status_complete
                    else -> R.string.work_status_ongoing
                },
            )
        }

    /** 正文：全文页一次拉全部章节，阅读器按章渲染 */
    override suspend fun novelChapters(work: Work): Result<List<NovelChapter>?> =
        repository.workDetail(work.id, full = true).map { detail ->
            detail.chapters.map { NovelChapter(title = it.title, text = it.text) }
                .filter { it.text.isNotBlank() }
                .takeIf { it.isNotEmpty() }
        }

    /** 兜底：不分章路径（章节接口已实现，正常不会走到） */
    override suspend fun novelBody(work: Work): Result<String> =
        repository.workDetail(work.id, full = true).map { it.fullText }

    companion object {
        const val FEED_UPDATED = "updated"
        const val FEED_TOP = "top"
        const val FEED_LATEST = "latest"

        /** 流 id → AO3 搜索端点的 sort_column（合法值实测自站内搜索表单） */
        val SORT_COLUMNS = mapOf(
            FEED_UPDATED to "revised_at",
            FEED_TOP to "kudos_count",
            FEED_LATEST to "created_at",
        )

        /** 排序。默认「最近更新」与站点标签页一致：回圈层先看"有什么新进展" */
        val FEEDS = listOf(
            SourceFeed(
                id = FEED_UPDATED,
                labelRes = R.string.ao3_tab_updated,
                chronological = true,
            ),
            SourceFeed(
                id = FEED_LATEST,
                labelRes = R.string.ao3_tab_latest,
                chronological = true,
            ),
            SourceFeed(
                id = FEED_TOP,
                labelRes = R.string.ao3_tab_top,
                // 按 kudos 排的名次基本稳定：刷新算「新增 N 条」会误报
                chronological = false,
            ),
        )

        const val FACET_COMPLETE = "complete"
        const val COMPLETE_ALL = "all"
        const val COMPLETE_YES = "yes"
        const val COMPLETE_NO = "no"
    }
}
