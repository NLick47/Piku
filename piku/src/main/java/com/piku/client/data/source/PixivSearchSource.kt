package com.piku.client.data.source

import com.piku.client.R
import com.piku.client.data.repository.PixivRepository
import com.piku.client.data.remote.pixiv.PixivAppConfig
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.SourceAuthorOpen
import com.piku.client.domain.source.FILTER_TOGGLE_ON
import com.piku.client.domain.source.SourcePage
import com.piku.client.domain.source.SearchFilterGroupSpec
import com.piku.client.domain.source.SearchFilterOptionSpec
import com.piku.client.domain.source.SearchFilterToggleSpec
import com.piku.client.domain.source.SourceSuggestion
import com.piku.client.domain.source.SourceSearch
import com.piku.client.domain.source.SourceTrendingTag
import javax.inject.Inject
import javax.inject.Singleton

/**
 * pixiv 检索插件：作品/用户搜索、标签联想、热门标签墙、检索筛选。
 * 筛选组与 app-api 参数一一对应（枚举值经 pixivpy 核对），不发明接口给不了的条件。
 */
@Singleton
class PixivSearchSource @Inject constructor(
    private val repository: PixivRepository,
) : SourceSearch {

    override val sourceId = WorkSource.PIXIV

    override val proportional = true

    override val supportsUsers = true

    override val filterGroups = FILTER_GROUPS

    override val filterToggles = FILTER_TOGGLES

    override suspend fun trendingTags(): Result<List<SourceTrendingTag>> =
        repository.trendingTags()

    override suspend fun suggest(query: String): Result<List<SourceSuggestion>> =
        repository.suggest(query)

    override suspend fun suggestTags(query: String): Result<List<SourceSuggestion>> =
        repository.suggestTags(query)

    override fun hiddenFilterGroups(selected: Map<String, String>): Set<String> =
        if (selected[SourceSearch.FILTER_KIND] == SourceSearch.KIND_NOVEL) {
            // 小说接口不支持按期间过滤，检索对象枚举也与作品不同：藏起来比置灰诚实
            setOf(GROUP_DURATION, GROUP_TARGET)
        } else {
            emptySet()
        }

    override suspend fun searchWorks(
        query: String,
        filters: Map<String, String>,
        page: Int,
    ): Result<SourcePage> {
        return repository.searchWorks(
            word = query,
            searchTarget = filters[GROUP_TARGET]?.ifBlank { null } ?: TARGET_PARTIAL,
            sort = filters[GROUP_SORT]?.ifBlank { null } ?: SORT_NEW,
            duration = filters[GROUP_DURATION]?.ifBlank { null },
            hideAi = filters[TOGGLE_HIDE_AI] == FILTER_TOGGLE_ON,
            page = page,
        )
    }

    override suspend fun searchNovels(
        query: String,
        filters: Map<String, String>,
        page: Int,
    ): Result<SourcePage> = repository.searchNovels(
        word = query,
        sort = filters[GROUP_SORT]?.ifBlank { null } ?: SORT_NEW,
        hideAi = filters[TOGGLE_HIDE_AI] == FILTER_TOGGLE_ON,
        offset = page * PixivAppConfig.PAGE_SIZE,
    )

    // 标签 tab 点中的是确定的标签，按完全一致检索，其余筛选照常生效
    override suspend fun searchTagWorks(
        tag: String,
        filters: Map<String, String>,
        page: Int,
    ): Result<SourcePage> = searchWorks(tag, filters + (GROUP_TARGET to TARGET_EXACT), page)

    override suspend fun searchUsers(query: String, page: Int): Result<List<FollowUser>> =
        repository.searchUsers(query, page * PixivAppConfig.PAGE_SIZE)

    override suspend fun toggleFollow(userId: Long, follow: Boolean): Result<Unit> =
        repository.followUser(userId, follow)

    // 画师主页由主壳承载，与详情页作者行一致
    override fun userPage(user: FollowUser): SourceAuthorOpen = SourceAuthorOpen.NativeProfile

    companion object {
        const val GROUP_SORT = "sort"
        const val GROUP_TARGET = "target"
        const val GROUP_DURATION = "duration"
        const val TOGGLE_HIDE_AI = "hide_ai"

        /** app-api sort 枚举 */
        const val SORT_NEW = "date_desc"
        const val SORT_OLD = "date_asc"
        const val SORT_POPULAR = "popular_desc"

        /** app-api search_target 枚举 */
        const val TARGET_PARTIAL = "partial_match_for_tags"
        const val TARGET_EXACT = "exact_match_for_tags"
        const val TARGET_TITLE = "title_and_caption"

        /** app-api duration 枚举；空串 = 全部（不带参数） */
        const val DURATION_ALL = ""
        const val DURATION_DAY = "within_last_day"
        const val DURATION_WEEK = "within_last_week"
        const val DURATION_MONTH = "within_last_month"

        /** 声明是纯数据，单独暴露以便不构造本类即可测试与断言 */
        val FILTER_GROUPS = listOf(
            SearchFilterGroupSpec(
                id = SourceSearch.FILTER_KIND,
                labelRes = R.string.search_filter_kind,
                options = listOf(
                    SearchFilterOptionSpec(
                        id = SourceSearch.KIND_ALL,
                        labelRes = R.string.search_filter_kind_all,
                        default = true,
                    ),
                    SearchFilterOptionSpec(
                        id = SourceSearch.KIND_NOVEL,
                        labelRes = R.string.search_filter_kind_novel,
                    ),
                ),
            ),
            SearchFilterGroupSpec(
                id = GROUP_SORT,
                labelRes = R.string.search_filter_sort,
                options = listOf(
                    SearchFilterOptionSpec(id = SORT_NEW, labelRes = R.string.search_filter_sort_new, default = true),
                    SearchFilterOptionSpec(id = SORT_OLD, labelRes = R.string.search_filter_sort_old),
                    SearchFilterOptionSpec(id = SORT_POPULAR, labelRes = R.string.search_filter_sort_popular),
                ),
            ),
            SearchFilterGroupSpec(
                id = GROUP_TARGET,
                labelRes = R.string.search_filter_target,
                options = listOf(
                    SearchFilterOptionSpec(id = TARGET_PARTIAL, labelRes = R.string.search_filter_target_partial, default = true),
                    SearchFilterOptionSpec(id = TARGET_EXACT, labelRes = R.string.search_filter_target_exact),
                    SearchFilterOptionSpec(id = TARGET_TITLE, labelRes = R.string.search_filter_target_title),
                ),
            ),
            SearchFilterGroupSpec(
                id = GROUP_DURATION,
                labelRes = R.string.search_filter_duration,
                options = listOf(
                    SearchFilterOptionSpec(id = DURATION_ALL, labelRes = R.string.search_filter_duration_all, default = true),
                    SearchFilterOptionSpec(id = DURATION_DAY, labelRes = R.string.search_filter_duration_day),
                    SearchFilterOptionSpec(id = DURATION_WEEK, labelRes = R.string.search_filter_duration_week),
                    SearchFilterOptionSpec(id = DURATION_MONTH, labelRes = R.string.search_filter_duration_month),
                ),
            ),
        )

        val FILTER_TOGGLES = listOf(
            SearchFilterToggleSpec(
                id = TOGGLE_HIDE_AI,
                labelRes = R.string.search_filter_hide_ai,
            ),
        )
    }
}
