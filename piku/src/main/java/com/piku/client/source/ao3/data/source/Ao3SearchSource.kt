package com.piku.client.source.ao3.data.source

import com.piku.client.R
import com.piku.client.core.model.WorkSource
import com.piku.client.core.source.SearchFilterGroupSpec
import com.piku.client.core.source.SearchFilterOptionSpec
import com.piku.client.core.source.SourcePage
import com.piku.client.core.source.SourceSearch
import com.piku.client.source.ao3.data.repository.Ao3Repository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AO3 检索：搜索端点无查询词也能出结果，靠排序驱动的浏览因此与检索共用一条链路
 * （详情页点标签进来也是这里：标签走 AO3 自己的标签页）。
 */
@Singleton
class Ao3SearchSource @Inject constructor(
    private val repository: Ao3Repository,
) : SourceSearch {

    override val sourceId = WorkSource.AO3

    /** 文字卡没有比例可言 */
    override val proportional = false

    /** AO3 的人名检索不在公开页面上，未接 */
    override val supportsUsers = false

    /** 只声明实测过的排序维度；分级/警告筛选的参数取值未验证，不摆出来 */
    override val filterGroups = listOf(
        SearchFilterGroupSpec(
            id = FILTER_SORT,
            labelRes = R.string.search_filter_sort,
            options = listOf(
                SearchFilterOptionSpec(
                    id = SORT_BEST_MATCH,
                    labelRes = R.string.ao3_sort_best,
                    default = true,
                ),
                SearchFilterOptionSpec(id = SORT_TOP, labelRes = R.string.search_filter_sort_popular),
                SearchFilterOptionSpec(id = SORT_HITS, labelRes = R.string.ao3_sort_hits),
                SearchFilterOptionSpec(id = SORT_WORDS, labelRes = R.string.ao3_sort_words),
                SearchFilterOptionSpec(id = SORT_LATEST, labelRes = R.string.search_filter_sort_new),
                SearchFilterOptionSpec(id = SORT_UPDATED, labelRes = R.string.ao3_sort_updated),
            ),
        ),
    )

    override suspend fun searchWorks(
        query: String,
        filters: Map<String, String>,
        page: Int,
    ): Result<SourcePage> = listing(query = query, tag = null, filters = filters, page = page)

    /** 标签 tab：走 AO3 的标签页（`/tags/{tag}/works`），与把标签名当关键词搜的结果不是一回事 */
    override suspend fun searchTagWorks(
        tag: String,
        filters: Map<String, String>,
        page: Int,
    ): Result<SourcePage> = listing(query = null, tag = tag, filters = filters, page = page)

    private suspend fun listing(
        query: String?,
        tag: String?,
        filters: Map<String, String>,
        page: Int,
    ): Result<SourcePage> {
        val sort = filters[FILTER_SORT] ?: SORT_BEST_MATCH
        // 竞速/标题排序不带查询词会回「syntax error」表单页：无查询词时落到默认排序
        val effective = if (query.isNullOrBlank() && !tag.isNullOrBlank() && sort == SORT_BEST_MATCH) {
            SORT_TOP
        } else {
            sort
        }
        return repository.listing(
            query = query,
            tag = tag,
            user = null,
            sortColumn = effective,
            // 外壳的页码从 0 起，AO3 从 1 起
            page = page + 1,
        ).map { (items, hasNext) ->
            SourcePage(items = items, totalPages = if (hasNext) null else page)
        }
    }

    companion object {
        const val FILTER_SORT = "sort"

        /** 站内搜索表单的默认排序（相关性） */
        const val SORT_BEST_MATCH = "_score"
        const val SORT_TOP = "kudos_count"
        const val SORT_HITS = "hits"
        const val SORT_WORDS = "word_count"
        const val SORT_LATEST = "created_at"
        const val SORT_UPDATED = "revised_at"
    }
}
