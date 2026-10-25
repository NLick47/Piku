package com.piku.client.source.ao3.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

object Ao3Config {
    const val BASE_URL = "https://archiveofourown.org/"
    const val MAIN_HOST = "archiveofourown.org"

    /** 本源的 client / cookie 罐 / 登录接口在 DI 里的限定名 */
    const val CLIENT_NAME = "ao3"

    /**
     * 桌面浏览器 UA。AO3 前端是 Cloudflare Bot Management，实测（2026-10）curl 裸 UA
     * 与自定义 UA 都放行，但站内若干响应带 `__cf_bm` cookie，浏览器形态的头更稳。
     */
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    /** 列表页与详情页都是大页面（列表 200KB、全文页 400KB+），超时按 30s 起步 */
    const val TIMEOUT_MS = 30_000L
}

interface Ao3Api {

    /**
     * 作品页（首章）。详情页的元数据用这一档：页面 40KB 上下，不带全文。
     *
     * `view_adult=true` = 站点那页「Yes, Continue」按钮的去向：R-18 作品不带这个参数会回
     * 一张 200 的成人内容警告页（正文/元数据都没有，解析器只会报 NotFound）。
     * 站点自己的门不重复过——应用侧 AO3 声明为不过 R-18 门，分级仍以卡片徽标提示。
     */
    @GET("works/{id}")
    suspend fun getWork(
        @Path("id") id: Long,
        @Query("view_adult") adult: Boolean = true,
    ): ResponseBody

    /**
     * 作品全章页。`view_full_work=true` 一页拿全部章节（实测 37 章作品 436KB），阅读用。
     * 注意与 [getWork] 分成两个方法：布尔型 Query 参数在 false 时也会拼进 URL，
     * 而 AO3 按参数有无判断，`view_full_work=false` 同样会回全文。
     */
    @GET("works/{id}")
    suspend fun getWorkFull(
        @Path("id") id: Long,
        @Query("view_full_work") fullWork: Boolean = true,
        @Query("view_adult") adult: Boolean = true,
    ): ResponseBody

    /**
     * 作品列表（搜索端点）。无查询词也能出结果：只给排序即可全站浏览
     * （2026-10 实测；`sort_column` 的合法值是 kudos_count / created_at /
     * revised_at / hits / comments_count / bookmarks_count / word_count / _score，
     * 传 `kudos` 这类短名会回「syntax error」）。
     *
     * [sortColumn] 缺省 = 不带排序参数，站点按默认排
     */
    @GET("works/search")
    suspend fun searchWorks(
        @Query("work_search[query]") query: String? = null,
        /** 圈层收窄（fandom_names/relationship_names/freeform_names 等），键含 `work_search[…]` 前缀 */
        @QueryMap scope: Map<String, String> = emptyMap(),
        @Query("work_search[sort_column]") sortColumn: String? = null,
        @Query("work_search[sort_direction]") sortDirection: String = "desc",
        @Query("page") page: Int = 1,
    ): ResponseBody

    /**
     * 标签搜索，联想与待机态热门榜都用（结果页 HTML 见 [Ao3TagSearchParser]）。
     *
     * 不用搜索表单那个 `/autocomplete/…` JSON 端点：它走 CF 机器人管理，应用里稳定 403
     * （2026-10 实测，主机上却是 200），普通页面 GET 才按页面放行。
     * `wrangling_status=canonical` 只回规范标签——圈层值必须是精确标签名，
     * 因为 `work_search[fandom_names]` 不做模糊匹配（传缩写会被静默忽略、返回全站结果）。
     * 排序按使用量降序：默认按名字母序时「Fluff」这种热门标签要翻到中段才出现。
     *
     * [name] 缺省 = 不限名字，站点按排序给该类型的全量；[type] 缺省 = 不限类型，一次回四种标签混排
     */
    @GET("tags/search")
    suspend fun searchTags(
        @Query("tag_search[name]") name: String?,
        /** Fandom / Character / Relationship / Freeform；缺省 = 不限 */
        @Query("tag_search[type]") type: String?,
        @Query("tag_search[wrangling_status]") status: String = "canonical",
        @Query("tag_search[sort_column]") sortColumn: String = "uses",
        @Query("tag_search[sort_direction]") sortDirection: String = "desc",
    ): ResponseBody

    /**
     * 标签下的作品列表。排序与搜索端点同一组参数，不带 = 站点默认排法；
     * 路径里的标签名要的是 [Ao3TagPath] 的转义形态，不是显示名
     */
    @GET("tags/{tag}/works")
    suspend fun getTagWorks(
        @Path("tag", encoded = true) tag: String,
        @Query("page") page: Int = 1,
        @Query("work_search[sort_column]") sortColumn: String? = null,
        @Query("work_search[sort_direction]") sortDirection: String = "desc",
    ): ResponseBody

    /** 用户主页的作品列表 */
    @GET("users/{user}/works")
    suspend fun getUserWorks(
        @Path("user", encoded = true) user: String,
        @Query("page") page: Int = 1,
    ): ResponseBody
}
