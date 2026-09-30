package com.piku.client.data.source

import com.piku.client.R
import com.piku.client.domain.model.PoipikuCategory
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.ContentSource
import com.piku.client.domain.source.SourceAuthorOpen
import com.piku.client.domain.source.SourceFacet
import com.piku.client.domain.source.SourceFacetGroup
import com.piku.client.domain.source.SourceFacetStyle
import com.piku.client.domain.source.SourceFeed
import com.piku.client.domain.source.SourcePage
import com.piku.client.domain.source.SourceWorkOpen
import com.piku.client.domain.source.SourceWorkPage
import com.piku.client.domain.usecase.LoadFeedUseCase
import com.piku.client.domain.usecase.LoadFollowFeedUseCase
import com.piku.client.domain.usecase.LoadPopularFeedUseCase
import com.piku.client.domain.usecase.LoadRandomFeedUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PoipikuContentSource @Inject constructor(
    private val loadFeedUseCase: LoadFeedUseCase,
    private val loadPopularFeedUseCase: LoadPopularFeedUseCase,
    private val loadFollowFeedUseCase: LoadFollowFeedUseCase,
    private val loadRandomFeedUseCase: LoadRandomFeedUseCase,
) : ContentSource {

    override val id = WorkSource.POIPIKU

    override val labelRes = R.string.home_source_poipiku

    override val feeds = FEEDS

    override val facets = listOf(
        SourceFacetGroup(
            id = FACET_CATEGORY,
            style = SourceFacetStyle.Dropdown,
            options = PoipikuCategory.entries.map { category ->
                SourceFacet(
                    id = category.cd.toString(),
                    labelRes = category.nameRes,
                    selectedByDefault = category == PoipikuCategory.ALL,
                )
            },
        ),
    )

    override suspend fun page(feedId: String, facets: Map<String, String>, page: Int): Result<SourcePage> {
        val works = when (feedId) {
            FEED_HOT -> loadPopularFeedUseCase(page)
            FEED_FOLLOW -> loadFollowFeedUseCase(page)
            FEED_RANDOM -> loadRandomFeedUseCase()
            else -> loadFeedUseCase(page, facets[FACET_CATEGORY]?.toIntOrNull() ?: PoipikuCategory.ALL.cd)
        }
        return works.map { list -> SourcePage(items = list) }
    }

    override fun open(work: Work): SourceWorkOpen = SourceWorkOpen.NativeDetail

    // 作者页是主壳的「用户作品」页（它按 poipiku 用户 id 取稿）
    override fun authorPage(work: Work): SourceAuthorOpen = SourceAuthorOpen.NativeDetail

    override suspend fun workPages(work: Work): Result<List<SourceWorkPage>> =
        // poipiku 的看图走专属详情（登录门/R-18 门/密码门都在那套链路里），不经通用查看器
        Result.success(emptyList())

    companion object {
        const val FEED_HOT = "hot"
        const val FEED_LATEST = "latest"
        const val FEED_FOLLOW = "follow"
        const val FEED_RANDOM = "random"
        const val FACET_CATEGORY = "category"

        /** 流声明是纯数据，单独暴露以便不构造本类（也就无需 DI）即可测试与断言 */
        val FEEDS = listOf(
            SourceFeed(id = FEED_HOT, labelRes = R.string.home_tab_hot),
            SourceFeed(id = FEED_LATEST, labelRes = R.string.home_tab_latest),
            SourceFeed(id = FEED_FOLLOW, labelRes = R.string.home_tab_follow, requiresLogin = true),
            // 随机流没有下一页，也没有时间序
            SourceFeed(
                id = FEED_RANDOM,
                labelRes = R.string.home_tab_random,
                paginated = false,
                chronological = false,
            ),
        )
    }
}
