package com.piku.client.data.source

import com.piku.client.R
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.repository.PixivRepository
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.ContentSource
import com.piku.client.domain.source.SourceFacet
import com.piku.client.domain.source.SourceFeed
import com.piku.client.domain.source.SourcePage
import com.piku.client.domain.source.SourceWorkOpen
import com.piku.client.domain.source.SourceWorkPage
import com.piku.client.domain.source.SourceWorkText
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PixivContentSource @Inject constructor(
    private val repository: PixivRepository,
    private val settingsRepository: SettingsRepository,
) : ContentSource {

    override val id = WorkSource.PIXIV

    override val labelRes = R.string.home_source_pixiv

    override val feeds = FEEDS

    override val facets = FACETS

    override suspend fun page(feedId: String, facetId: String?, page: Int): Result<SourcePage> {
        val adultEnabled = settingsRepository.showAdultContent.first()
        return repository.ranking(
            mode = feedId,
            content = facetId ?: FACET_ALL,
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

    companion object {
        const val FACET_ALL = "all"

        val FEEDS = listOf(
            SourceFeed(id = "daily", labelRes = R.string.pixiv_tab_daily),
            SourceFeed(id = "weekly", labelRes = R.string.pixiv_tab_weekly),
            SourceFeed(id = "monthly", labelRes = R.string.pixiv_tab_monthly),
            SourceFeed(id = "rookie", labelRes = R.string.pixiv_tab_rookie),
        )

        val FACETS = listOf(
            SourceFacet(id = FACET_ALL, labelRes = R.string.pixiv_filter_all, selectedByDefault = true),
            SourceFacet(id = "illust", labelRes = R.string.pixiv_filter_illust),
            SourceFacet(id = "manga", labelRes = R.string.pixiv_filter_manga),
        )
    }
}
