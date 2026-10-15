package com.piku.client.source.ao3.data.repository

import android.util.Log
import com.piku.client.BuildConfig
import com.piku.client.core.model.Work
import com.piku.client.core.source.SourceScope
import com.piku.client.core.network.http.apiCall
import com.piku.client.source.ao3.data.remote.Ao3Api
import com.piku.client.source.ao3.data.remote.Ao3ListingParser
import com.piku.client.source.ao3.data.remote.Ao3ScopeParams
import com.piku.client.source.ao3.data.remote.Ao3TagSearchParser
import com.piku.client.source.ao3.data.remote.Ao3WorkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AO3 取数。AO3 没有公开 API，全靠 HTML 解析（见 [Ao3ListingParser] / [Ao3WorkParser]）。
 *
 * 礼貌间隔：AO3 的 robots.txt 对 Slurp 声明 Crawl-delay 30，虽不约束普通 UA，
 * 实测（2026-10）间隔 1s 的连续请求无 429。列表与详情分开限流：
 * 翻页时一次点按可能连带触发详情，两条链各持一份时间戳。
 */
@Singleton
class Ao3Repository @Inject constructor(
    private val api: Ao3Api,
) {

    private var lastListingAt = 0L
    private var lastDetailAt = 0L
    private var lastSuggestionAt = 0L

    /** 列表页的作品条目，返回 (作品, 是否还有下一页)；[scope] 非空时按圈层收窄 */
    suspend fun listing(
        query: String?,
        tag: String?,
        user: String?,
        sortColumn: String,
        page: Int,
        /** 除圈层外的附加收窄（完结状态/字数下限），与圈层参数合并后一起发出 */
        extra: Map<String, String> = emptyMap(),
        scope: SourceScope? = null,
    ): Result<Pair<List<Work>, Boolean>> = paceListing {
        val html = when {
            user != null -> api.getUserWorks(user = user, page = page).string()
            tag != null -> api.getTagWorks(tag = tag, page = page).string()
            else -> api.searchWorks(
                query = query,
                scope = extra,
                sortColumn = sortColumn,
                page = page,
            ).string()
        }
        withContext(Dispatchers.Default) {
            Ao3ListingParser.parse(html) to Ao3ListingParser.hasNextPage(html)
        }
    }

    /**
     * 作品详情。[full] = true 走 `view_full_work=true` 一页取全部章节（阅读用）；
     * false 只取首章页（详情页的元数据够用，页面小得多）。
     */
    suspend fun workDetail(workId: Long, full: Boolean = false): Result<Ao3WorkParser.Ao3WorkDetail> =
        paceDetail {
            val html = if (full) {
                api.getWorkFull(workId).string()
            } else {
                api.getWork(workId).string()
            }
            val detail = withContext(Dispatchers.Default) { Ao3WorkParser.parse(html, workId) }
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "ao3 workDetail id=$workId full=$full bytes=${html.length} " +
                        "chapters=${detail.chapters.size} body=${detail.chapters.sumOf { it.text.length }}",
                )
            }
            detail
        }

    /**
     * 圈层选择器的标签联想。AO3 的搜索字段不做模糊匹配（`work_search[fandom_names]=harry`
     * 会被静默忽略、返回全站结果），圈层值必须是精确标签名，所以这一步不可省。
     */
    suspend fun tagSuggestions(name: String, type: String): Result<List<Ao3TagSearchParser.Suggestion>> =
        pace(::lastSuggestionAt, SUGGESTION_INTERVAL_MS) { lastSuggestionAt = it }
            .let {
                apiCall {
                    val html = api.searchTags(name = name, type = type).string()
                    withContext(Dispatchers.Default) { Ao3TagSearchParser.parse(html) }
                }
            }
            .onFailure { Log.w(TAG, "ao3 tag search failed: $it") }

    private suspend fun <T> paceListing(block: suspend () -> T): Result<T> =
        pace(::lastListingAt, LISTING_INTERVAL_MS) { lastListingAt = it }
            .let { apiCall { block() } }
            .onFailure { Log.w(TAG, "ao3 listing failed: $it") }

    private suspend fun <T> paceDetail(block: suspend () -> T): Result<T> =
        pace(::lastDetailAt, DETAIL_INTERVAL_MS) { lastDetailAt = it }
            .let { apiCall { block() } }
            .onFailure { Log.w(TAG, "ao3 detail failed: $it") }

    /** 距上次同链请求不足间隔则等齐：AO3 是公益站，抓取要克制 */
    private suspend fun pace(last: () -> Long, interval: Long, set: (Long) -> Unit) {
        val now = System.currentTimeMillis()
        val wait = interval - (now - last())
        if (wait > 0) delay(wait)
        set(System.currentTimeMillis())
    }

    private companion object {
        const val TAG = "PikuDiag"

        /** 实测间隔 1s 无 429，取 1.2s 留余量 */
        const val LISTING_INTERVAL_MS = 1_200L

        /** 详情页更重（全文页 400KB+），间隔放大 */
        const val DETAIL_INTERVAL_MS = 1_500L

        /** 联想是打字触发的连发请求（选择器里每停顿一次一次），也是页面级 GET，同样要克制 */
        const val SUGGESTION_INTERVAL_MS = 1_200L
    }
}
