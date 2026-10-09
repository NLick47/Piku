package com.piku.client.data.source

import com.piku.client.R
import com.piku.client.data.remote.pixiv.PixivAppConfig
import com.piku.client.data.repository.PixivRepository
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.model.WorkKind
import com.piku.client.domain.source.ContentSource
import com.piku.client.domain.source.AuthorPageStyle
import com.piku.client.domain.source.SourceAuthorOpen
import com.piku.client.domain.source.SourceFacet
import com.piku.client.domain.source.SourceFacetGroup
import com.piku.client.domain.source.SourceFacetStyle
import com.piku.client.domain.source.SourceFacetVisibleWhen
import com.piku.client.domain.source.SourceFeed
import com.piku.client.domain.source.SourcePage
import com.piku.client.domain.source.SourceWorkOpen
import com.piku.client.domain.source.SourceWorkPage
import com.piku.client.domain.source.SourceWorkText
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PixivContentSource @Inject constructor(
    private val repository: PixivRepository,
) : ContentSource {

    override val id = WorkSource.PIXIV

    override val labelRes = R.string.home_source_pixiv

    override val loginPromptRes = R.string.pixiv_need_login

    override val feeds = FEEDS

    override val facets = FACETS

    override suspend fun page(feedId: String, facets: Map<String, String>, page: Int): Result<SourcePage> {
        // 登录门未开前其余流不可达；真到达即实现缺口，给终态而非空页
        if (feedId !in IMPLEMENTED_FEEDS) return Result.failure(AppError.NotFound)
        val offset = page * PixivAppConfig.PAGE_SIZE
        val result = when (feedId) {
            FEED_RECOMMEND -> if (facets[GROUP_CONTENT] == FACET_NOVEL) {
                repository.novelRecommended(offset).map { items -> SourcePage(items = items) }
            } else {
                // 插画推荐接口自带 content_type：漫画档要传下去，否则选了漫画还是回插画
                repository.recommendedFeed(
                    offset = offset,
                    contentType = facets[GROUP_CONTENT] ?: FACET_ILLUST,
                ).map { items -> SourcePage(items = items) }
            }

            FEED_FOLLOW -> {
                // 公开/悄悄关注两个范围：接口没有「全部」档，一次请求只能选一边
                val restrict = facets[GROUP_FOLLOW_SCOPE] ?: PixivAppConfig.RESTRICT_PUBLIC
                if (facets[GROUP_CONTENT] == FACET_NOVEL) {
                    repository.novelFollowFeed(offset = offset, restrict = restrict).map { items -> SourcePage(items = items) }
                } else {
                    repository.followFeed(offset = offset, restrict = restrict).map { items -> SourcePage(items = items) }
                }
            }

            FEED_LATEST -> latestPage(facets, page)

            else -> {
                // 榜单下拉兼任两类：综合族（content 参数有效，周期 chips 随它显示）与
                // 独立榜（男生向/女生向/原创，单一日榜：mode 直接用选项值，content 实测只认 all，
                // illust/manga 404；周期 chips 对独立榜整组隐藏）
                val board = facets[GROUP_CONTENT] ?: FACET_ALL
                val family = board in RANKING_FAMILIES
                repository.ranking(
                    mode = if (family) board else facets[GROUP_PERIOD] ?: PERIOD_DAILY,
                    content = if (family) FACET_ALL else board,
                    page = page + 1,
                )
                    // 翻过末页接口回 404（2026-09 实测）而非空列表：翻页中的 NotFound 就地判到底，
                    // 首屏 404（模式/类型无效）照常失败
                    .recoverCatching { error ->
                        if (page > 0 && error == AppError.NotFound) SourcePage(items = emptyList()) else throw error
                    }
            }
        }
        // R-18/敏感的下发由 pixiv 账号侧表示设置在服务端管控，客户端不再过滤
        return result
    }

    private suspend fun latestPage(facets: Map<String, String>, page: Int): Result<SourcePage> {
        val contentType = facets[GROUP_LATEST_CONTENT] ?: FACET_ILLUST
        if (contentType == FACET_NOVEL) {
            val cursor = latestCursors["$contentType:$page"]
            if (page > 0 && cursor == null) return Result.success(SourcePage(items = emptyList()))
            return repository.novelNew(cursor?.toLongOrNull())
                .onSuccess { next ->
                    next.nextCursor?.let { latestCursors["$contentType:${page + 1}"] = it }
                }
        }
        val cursor = latestCursors["$contentType:$page"]
        if (page > 0 && cursor == null) return Result.success(SourcePage(items = emptyList()))
        return repository.newFeed(contentType, cursor?.toLongOrNull())
            .onSuccess { next ->
                next.nextCursor?.let { latestCursors["$contentType:${page + 1}"] = it }
            }
    }

    /** 新着流各页的翻页游标，键 =「内容档:页码」 */
    private val latestCursors = mutableMapOf<String, String>()

    override fun open(work: Work): SourceWorkOpen = SourceWorkOpen.InAppViewer

    // 画师主页由主壳承载（资料区 + 分类 Tab + 作品墙），不再出站
    override fun authorPage(work: Work): SourceAuthorOpen = SourceAuthorOpen.NativeProfile

    override val authorPageStyle: AuthorPageStyle = AuthorPageStyle.Profile

    override suspend fun workPages(work: Work): Result<List<SourceWorkPage>> =
        repository.workPages(work.id)

    override suspend fun workDetailText(work: Work): Result<SourceWorkText?> =
        if (work.kind == WorkKind.NOVEL) repository.novelText(work.id) else repository.workText(work.id)

    override suspend fun novelBody(work: Work): Result<String> = repository.novelBody(work.id)

    override suspend fun relatedWorks(
        work: Work,
        onPage: suspend (List<Work>) -> Unit,
    ): Result<List<Work>> = repository.recommend(work.id, onPage)

    companion object {
        const val FEED_RECOMMEND = "recommend"
        const val FEED_RANKING = "ranking"
        const val FEED_FOLLOW = "follow"
        const val FEED_LATEST = "latest"
        const val GROUP_PERIOD = "period"
        const val GROUP_CONTENT = "content"
        const val GROUP_FOLLOW_SCOPE = "follow_scope"
        const val GROUP_LATEST_CONTENT = "latest_content"
        const val PERIOD_DAILY = "daily"
        const val FACET_ALL = "all"
        const val FACET_ILLUST = "illust"
        const val FACET_MANGA = "manga"
        const val FACET_NOVEL = "novel"

        /** 独立榜（非综合族）：只有单一日榜、content 参数只认 all，选项 id 直接用 ranking.php 的 mode 值（2026-10 实测） */
        val RANKING_FAMILIES = setOf("male", "female", "original")

        /** 已接通的流；未列进的声明流视为占位（能力未到），取页给终态而不是空页 */
        val IMPLEMENTED_FEEDS = setOf(FEED_RECOMMEND, FEED_FOLLOW, FEED_RANKING, FEED_LATEST)

        /** 声明是纯数据，单独暴露以便不构造本类（也就无需 DI）即可测试与断言 */
        val FEEDS = listOf(
            SourceFeed(
                id = FEED_RECOMMEND,
                labelRes = R.string.pixiv_tab_recommend,
                requiresLogin = true,
                proportional = true,
                // 推荐结果基本稳定：刷新算「新增 N 条」会误报
                chronological = false,
            ),
            SourceFeed(
                id = FEED_FOLLOW,
                labelRes = R.string.pixiv_tab_follow_new,
                requiresLogin = true,
                proportional = true,
            ),
            SourceFeed(id = FEED_RANKING, labelRes = R.string.pixiv_tab_ranking, ranked = true),
            SourceFeed(
                id = FEED_LATEST,
                labelRes = R.string.pixiv_tab_new,
                requiresLogin = true,
                proportional = true,
            ),
        )

        val FACETS = listOf(
            // 周期片选只对综合族有意义：榜单下拉切到独立榜（男生向/女生向/原创）时整组隐藏
            SourceFacetGroup(
                id = GROUP_PERIOD,
                style = SourceFacetStyle.Chips,
                feedId = FEED_RANKING,
                visibleOnlyWhen = SourceFacetVisibleWhen(
                    groupId = GROUP_CONTENT,
                    optionIds = setOf(FACET_ALL, FACET_ILLUST, FACET_MANGA),
                ),
                options = listOf(
                    SourceFacet(
                        id = PERIOD_DAILY,
                        labelRes = R.string.pixiv_tab_daily,
                        selectedByDefault = true,
                    ),
                    SourceFacet(id = "weekly", labelRes = R.string.pixiv_tab_weekly),
                    SourceFacet(id = "monthly", labelRes = R.string.pixiv_tab_monthly),
                    SourceFacet(id = "rookie", labelRes = R.string.pixiv_tab_rookie),
                ),
            ),
            // 榜单下拉：综合族（综合/插画/漫画）+ 独立榜（单一日榜）。tab 行只容一个下拉，内容与榜单家族合一个选择器
            SourceFacetGroup(
                id = GROUP_CONTENT,
                style = SourceFacetStyle.Dropdown,
                feedId = FEED_RANKING,
                options = listOf(
                    SourceFacet(id = FACET_ALL, labelRes = R.string.pixiv_filter_all, selectedByDefault = true),
                    SourceFacet(id = FACET_ILLUST, labelRes = R.string.pixiv_filter_illust),
                    SourceFacet(id = FACET_MANGA, labelRes = R.string.pixiv_filter_manga, dividerAfter = true),
                    SourceFacet(id = "male", labelRes = R.string.pixiv_ranking_male),
                    SourceFacet(id = "female", labelRes = R.string.pixiv_ranking_female),
                    SourceFacet(id = "original", labelRes = R.string.pixiv_ranking_original),
                ),
            ),
            // 推荐流的内容类型：插画/漫画/小说。小说走独立的推荐端点，不与插画混排
            SourceFacetGroup(
                id = GROUP_CONTENT,
                style = SourceFacetStyle.Dropdown,
                feedId = FEED_RECOMMEND,
                options = listOf(
                    SourceFacet(id = FACET_ILLUST, labelRes = R.string.pixiv_filter_illust, selectedByDefault = true),
                    SourceFacet(id = FACET_MANGA, labelRes = R.string.pixiv_filter_manga),
                    SourceFacet(id = FACET_NOVEL, labelRes = R.string.pixiv_filter_novel),
                ),
            ),
            // 关注流只有插画与小说两档：关注流接口不分插画/漫画
            SourceFacetGroup(
                id = GROUP_CONTENT,
                style = SourceFacetStyle.Dropdown,
                feedId = FEED_FOLLOW,
                options = listOf(
                    SourceFacet(id = FACET_ILLUST, labelRes = R.string.pixiv_filter_illust, selectedByDefault = true),
                    SourceFacet(id = FACET_NOVEL, labelRes = R.string.pixiv_filter_novel),
                ),
            ),
            // 关注流的可见范围：公开关注/悄悄关注，与关注列表的双 tab 同一词汇，id 直接用 restrict 参数值。
            // 常显片选而不进 tab 行：tab 行已有内容档下拉，EN/JA 长文案下再挂一个下拉小屏放不下（榜单周期片选同款）
            SourceFacetGroup(
                id = GROUP_FOLLOW_SCOPE,
                style = SourceFacetStyle.Chips,
                feedId = FEED_FOLLOW,
                options = listOf(
                    SourceFacet(id = PixivAppConfig.RESTRICT_PUBLIC, labelRes = R.string.pixiv_follow_scope_public, selectedByDefault = true),
                    SourceFacet(id = PixivAppConfig.RESTRICT_PRIVATE, labelRes = R.string.pixiv_follow_scope_quiet),
                ),
            ),
            // 新着的内容类型：插画/漫画/小说。app-api 对空 content_type（全部混排）的行为未验证，先不提供「全部」
            SourceFacetGroup(
                id = GROUP_LATEST_CONTENT,
                style = SourceFacetStyle.Dropdown,
                feedId = FEED_LATEST,
                options = listOf(
                    SourceFacet(id = FACET_ILLUST, labelRes = R.string.pixiv_filter_illust, selectedByDefault = true),
                    SourceFacet(id = FACET_MANGA, labelRes = R.string.pixiv_filter_manga),
                    SourceFacet(id = FACET_NOVEL, labelRes = R.string.pixiv_filter_novel),
                ),
            ),
        )
    }
}
