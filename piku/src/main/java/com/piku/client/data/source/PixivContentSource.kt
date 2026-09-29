package com.piku.client.data.source

import com.piku.client.R
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.repository.PixivRepository
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.ContentSource
import com.piku.client.domain.source.SourceFacet
import com.piku.client.domain.source.SourceFacetGroup
import com.piku.client.domain.source.SourceFacetStyle
import com.piku.client.domain.source.SourceFeed
import com.piku.client.domain.source.SourcePage
import com.piku.client.domain.source.SourceWorkOpen
import com.piku.client.domain.source.SourceWorkPage
import com.piku.client.domain.source.SourceWorkText
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * pixiv 作为内容源。tab 只占两个：推荐（登录后的个性化，先占位）与榜单；
 * 日/周/月/新人是榜单的周期片选（高频，常显），综合/插画/漫画挂行尾下拉（低频）。
 * 榜单一天只更新一次，占四个 tab 会把 tab 行浪费掉。
 * novel 与 *_r18 匿名返回空，不声明。榜单接口 p 从 1 计，这里的 page 从 0 计，取页时翻译。
 */
@Singleton
class PixivContentSource @Inject constructor(
    private val repository: PixivRepository,
    private val settingsRepository: SettingsRepository,
) : ContentSource {

    override val id = WorkSource.PIXIV

    override val labelRes = R.string.home_source_pixiv

    override val feeds = FEEDS

    override val facets = FACETS

    override suspend fun page(feedId: String, facets: Map<String, String>, page: Int): Result<SourcePage> {
        val adultEnabled = settingsRepository.showAdultContent.first()
        return repository.ranking(
            mode = facets[GROUP_PERIOD] ?: PERIOD_DAILY,
            content = facets[GROUP_CONTENT] ?: FACET_ALL,
            page = page + 1,
        )
            // 翻过末页接口回 404（2026-09 实测）而非空列表：翻页中的 NotFound 就地判到底，
            // 首屏 404（模式/类型无效）照常失败
            .recoverCatching { error ->
                if (page > 0 && error == AppError.NotFound) SourcePage(items = emptyList()) else throw error
            }
            .map { result ->
                result.copy(items = if (adultEnabled) result.items else result.items.filterNot { it.r18 })
            }
    }

    override fun open(work: Work): SourceWorkOpen = SourceWorkOpen.InAppViewer

    override suspend fun workPages(work: Work): Result<List<SourceWorkPage>> =
        repository.workPages(work.id)

    override suspend fun workDetailText(work: Work): Result<SourceWorkText?> =
        repository.workText(work.id)

    override suspend fun relatedWorks(work: Work): Result<List<Work>> =
        repository.recommend(work.id)

    companion object {
        const val FEED_RECOMMEND = "recommend"
        const val FEED_RANKING = "ranking"
        const val GROUP_PERIOD = "period"
        const val GROUP_CONTENT = "content"
        const val PERIOD_DAILY = "daily"
        const val FACET_ALL = "all"

        /** 声明是纯数据，单独暴露以便不构造本类（也就无需 DI）即可测试与断言 */
        val FEEDS = listOf(
            // 登录后的个性化推荐：接口与登录都未就绪，先占位；未登录时壳自动跳过它选榜单
            SourceFeed(id = FEED_RECOMMEND, labelRes = R.string.pixiv_tab_recommend, comingSoon = true),
            SourceFeed(id = FEED_RANKING, labelRes = R.string.pixiv_tab_ranking, ranked = true),
        )

        val FACETS = listOf(
            SourceFacetGroup(
                id = GROUP_PERIOD,
                style = SourceFacetStyle.Chips,
                options = listOf(
                    SourceFacet(
                        id = PERIOD_DAILY,
                        labelRes = R.string.pixiv_tab_daily,
                        selectedByDefault = true,
                        hintRes = R.string.pixiv_hint_daily,
                    ),
                    SourceFacet(id = "weekly", labelRes = R.string.pixiv_tab_weekly, hintRes = R.string.pixiv_hint_weekly),
                    SourceFacet(id = "monthly", labelRes = R.string.pixiv_tab_monthly, hintRes = R.string.pixiv_hint_monthly),
                    SourceFacet(id = "rookie", labelRes = R.string.pixiv_tab_rookie, hintRes = R.string.pixiv_hint_rookie),
                ),
            ),
            SourceFacetGroup(
                id = GROUP_CONTENT,
                style = SourceFacetStyle.Dropdown,
                options = listOf(
                    SourceFacet(id = FACET_ALL, labelRes = R.string.pixiv_filter_all, selectedByDefault = true),
                    SourceFacet(id = "illust", labelRes = R.string.pixiv_filter_illust),
                    SourceFacet(id = "manga", labelRes = R.string.pixiv_filter_manga),
                ),
            ),
        )
    }
}
