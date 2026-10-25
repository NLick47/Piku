package com.piku.client.source.poipiku.data.source

import com.piku.client.R
import com.piku.client.source.poipiku.data.model.PoipikuCategory
import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkSource
import com.piku.client.core.source.ContentSource
import com.piku.client.core.source.LIBRARY_POOL_MEDIA
import com.piku.client.core.source.SourceAuthorOpen
import com.piku.client.core.source.SourceFacet
import com.piku.client.core.source.SourceFacetGroup
import com.piku.client.core.source.SourceFacetStyle
import com.piku.client.core.source.SourceFeed
import com.piku.client.core.source.SourcePage
import com.piku.client.core.source.SourceScope
import com.piku.client.core.source.SourceWorkOpen
import com.piku.client.core.source.SourceWorkPage
import com.piku.client.source.poipiku.domain.usecase.LoadFeedUseCase
import com.piku.client.source.poipiku.domain.usecase.LoadFollowFeedUseCase
import com.piku.client.source.poipiku.domain.usecase.LoadPopularFeedUseCase
import com.piku.client.source.poipiku.domain.usecase.LoadRandomFeedUseCase
import com.piku.client.source.poipiku.domain.usecase.LoadWorkDetailUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PoipikuContentSource @Inject constructor(
    private val loadFeedUseCase: LoadFeedUseCase,
    private val loadPopularFeedUseCase: LoadPopularFeedUseCase,
    private val loadFollowFeedUseCase: LoadFollowFeedUseCase,
    private val loadRandomFeedUseCase: LoadRandomFeedUseCase,
    private val loadWorkDetailUseCase: LoadWorkDetailUseCase,
) : ContentSource {

    override val id = WorkSource.POIPIKU

    override val labelRes = R.string.home_source_poipiku

    /** 与 pixiv 共用图文资料库：收藏/历史里两边混排 */
    override val libraryPool = LIBRARY_POOL_MEDIA

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

    override suspend fun page(
        feedId: String,
        facets: Map<String, String>,
        page: Int,
        scope: SourceScope?,
    ): Result<SourcePage> {
        val works = when (feedId) {
            FEED_HOT -> loadPopularFeedUseCase(page)
            FEED_FOLLOW -> loadFollowFeedUseCase(page)
            FEED_RANDOM -> loadRandomFeedUseCase()
            else -> loadFeedUseCase(page, facets[FACET_CATEGORY]?.toIntOrNull() ?: PoipikuCategory.ALL.cd)
        }
        return works.map { list -> SourcePage(items = list) }
    }

    override fun open(work: Work): SourceWorkOpen = SourceWorkOpen.NativeDetail

    override fun webUrl(work: Work): String =
        "https://poipiku.com/${work.authorId}/${work.id}.html"

    // 作者页是主壳的「用户作品」页（它按 poipiku 用户 id 取稿）
    override fun authorPage(work: Work): SourceAuthorOpen = SourceAuthorOpen.NativeDetail

    override suspend fun workPages(work: Work): Result<List<SourceWorkPage>> =
        // poipiku 的看图走专属详情（登录门/R-18 门/密码门都在那套链路里），不经通用查看器
        Result.success(emptyList())

    /**
     * 正文随详情一起回来：缓存页的「更新到最新」要单独取文，这里补一条取文通道。
     * 密码/门禁作品的详情拿不到正文（detail.novelText 为空），取文会失败——它们本来也不该有缓存。
     */
    override suspend fun novelBody(work: Work): Result<String> =
        loadWorkDetailUseCase(work).map { it.novelText }

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
