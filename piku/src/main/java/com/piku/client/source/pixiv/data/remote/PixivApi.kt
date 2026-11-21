package com.piku.client.source.pixiv.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

object PixivApiConfig {

    const val BASE_URL = "https://www.pixiv.net/"

    const val PAGE_SIZE = 50

    const val RECOMMEND_LIMIT = 180

    const val RELATED_MAX_PAGES = 6

    /** 网页端检索端点：类型筛选只能靠换端点（type/mode 参数服务端忽略） */
    const val SEARCH_ARTWORKS = "ajax/search/artworks/"
    const val SEARCH_ILLUST = "ajax/search/illustrations/"
    const val SEARCH_MANGA = "ajax/search/manga/"

    val DEBUG_PROXY: String? = null
}

@Serializable
data class PixivRankingResponse(
    val contents: List<PixivRankingItem> = emptyList(),
    val mode: String = "",
    val content: String = "",
    val page: Int = 1,
    @SerialName("rank_total") val rankTotal: Int = 0,
)

@Serializable
data class PixivRankingItem(
    @SerialName("illust_id") val illustId: Long = 0,
    @SerialName("user_id") val userId: Long = 0,
    @SerialName("user_name") val userName: String = "",
    @SerialName("profile_img") val profileImg: String? = null,
    val title: String = "",
    val url: String = "",
    val tags: List<String> = emptyList(),
    /** 页数：正常是字符串，占位条目给数字（见 PixivWireTolerance） */
    @SerialName("illust_page_count")
    @Serializable(with = FlexibleStringSerializer::class)
    val illustPageCount: String = "1",
    @SerialName("illust_content_type")
    @Serializable(with = PixivContentTypeSerializer::class)
    val contentType: PixivContentType = PixivContentType(),
    /** 作品真实宽高（缩略图是裁切档，比例得按这个算） */
    val width: Int = 0,
    val height: Int = 0,
) {
    val pageCount: Int get() = illustPageCount.toIntOrNull() ?: 1
}

/** 只取分级用到的字段；占位条目会发空数组而非对象，容错见 [PixivContentTypeSerializer] */
@Serializable(with = PixivContentTypeSerializer::class)
data class PixivContentType(
    /** 0=全年龄 1=R-18 2=R-18G */
    val sexual: Int = 0,
)

@Serializable
data class PixivPagesResponse(
    val error: Boolean = false,
    val body: List<PixivPage> = emptyList(),
)

@Serializable
data class PixivPage(
    val width: Int = 0,
    val height: Int = 0,
    val urls: PixivPageUrls = PixivPageUrls(),
)

@Serializable
data class PixivPageUrls(
    @SerialName("thumb_mini") val thumbMini: String = "",
    val small: String = "",
    val regular: String = "",
    val original: String = "",
)

@Serializable
data class PixivIllustResponse(
    val error: Boolean = false,
    val body: PixivIllustBody = PixivIllustBody(),
)

/**
 * 只取详情页用得到的字段。计数类（view/bookmark）在匿名访问下也在，
 * 但个别作品（限制公开、接口降级）会缺，缺了按默认值 0 处理——UI 只展示有意义的项。
 */
@Serializable
data class PixivIllustBody(
    val description: String = "",
    val tags: PixivIllustTags = PixivIllustTags(),
    @SerialName("viewCount") val viewCount: Int = 0,
    @SerialName("bookmarkCount") val bookmarkCount: Int = 0,
    @SerialName("pageCount") val pageCount: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    /** 上传时间；个别作品只有 createDate */
    @SerialName("uploadDate") val uploadDate: String = "",
    @SerialName("createDate") val createDate: String = "",
    @SerialName("userAccount") val userAccount: String = "",
)

@Serializable
data class PixivRecommendResponse(
    val error: Boolean = false,
    val body: PixivRecommendBody = PixivRecommendBody(),
)

@Serializable
data class PixivNovelAjaxResponse(
    val error: Boolean = false,
    val body: PixivNovelAjaxBody = PixivNovelAjaxBody(),
)

@Serializable
data class PixivNovelAjaxBody(
    /** 正文原文，pixiv 私有标记未清洗 */
    val content: String = "",
    @SerialName("textEmbeddedImages") val textEmbeddedImages: Map<String, PixivNovelEmbeddedImage> = emptyMap(),
    /** 详情页补充（未登录的小说详情/正文都从这里取）：HTML 简介、标签、统计 */
    val description: String = "",
    val tags: PixivNovelAjaxTags = PixivNovelAjaxTags(),
    @SerialName("viewCount") val viewCount: Int = 0,
    @SerialName("likeCount") val likeCount: Int = 0,
    @SerialName("bookmarkCount") val bookmarkCount: Int = 0,
    @SerialName("createDate") val createDate: String = "",
)

/** 网页端小说标签：外层带 authorId/isLocked，真正列表在 tags.tags 里 */
@Serializable
data class PixivNovelAjaxTags(
    val tags: List<PixivNovelAjaxTag> = emptyList(),
)

@Serializable
data class PixivNovelAjaxTag(
    val tag: String = "",
)

@Serializable
data class PixivRecommendBody(
    val illusts: List<PixivWorkCard> = emptyList(),
    /** 首屏没装下的候选 id（wire 上是字符串数组）；本接口自己不翻页，余量交 recommendByIds 换卡片 */
    val nextIds: List<String> = emptyList(),
)

/** 作品卡片（推荐位）；只取成卡需要的字段 */
@Serializable
data class PixivWorkCard(
    val id: String = "",
    val title: String = "",
    /** 360x360 缩略图 */
    val url: String = "",
    @SerialName("userId") val userId: String = "",
    @SerialName("userName") val userName: String = "",
    @SerialName("pageCount") val pageCount: Int = 1,
    @SerialName("xRestrict") val xRestrict: Int = 0,
    /** 作品真实宽高（缩略图是方裁，比例得按这个算） */
    val width: Int = 0,
    val height: Int = 0,
) {
    val illustId: Long get() = id.toLongOrNull() ?: 0
    val authorIdLong: Long get() = userId.toLongOrNull() ?: 0
}

@Serializable
data class PixivIllustTags(
    val tags: List<PixivTag> = emptyList(),
)

@Serializable
data class PixivTag(val tag: String = "")

@Serializable
data class PixivSearchResponse(
    val error: Boolean = false,
    val body: PixivSearchBody = PixivSearchBody(),
)

/**
 * 网页端检索信封。三个检索端点各回各的桶名，参数完全一致：
 * artworks → illustManga（插画/漫画/动图混排）、illustrations → illust（不含漫画）、
 * manga → manga（只有漫画）。类型筛选在网页端只能靠换端点，`type`/`mode` 参数服务端直接忽略。
 */
@Serializable
data class PixivSearchBody(
    val illustManga: PixivSearchResult? = null,
    val illust: PixivSearchResult? = null,
    val manga: PixivSearchResult? = null,
) {
    /** 取实际回来的那个桶；都没有（空壳信封）给空结果 */
    val works: PixivSearchResult
        get() = illustManga ?: illust ?: manga ?: PixivSearchResult()
}

@Serializable
data class PixivSearchResult(
    val data: List<PixivSearchItem> = emptyList(),
    val total: Int = 0,
    /** 翻页上限（1 基页码）。匿名实测钳在 10 页，越界请求返回的数据与末页相同 */
    @SerialName("lastPage") val lastPage: Int = 0,
)

/** 网页端搜索条目；url 是方裁缩略图（/c/250x250_80_a2/ 前缀），升清档时改写为未裁切 master1200 */
@Serializable
data class PixivSearchItem(
    @Serializable(with = FlexibleStringSerializer::class) val id: String = "",
    val title: String = "",
    val url: String = "",
    @SerialName("userId") val userId: String = "",
    @SerialName("userName") val userName: String = "",
    @SerialName("profileImageUrl") val profileImageUrl: String = "",
    @SerialName("pageCount") val pageCount: Int = 1,
    @SerialName("xRestrict") val xRestrict: Int = 0,
    /** 1=非 AI 生成 2=AI 生成（app-api 同义字段叫 illustAiType） */
    @SerialName("aiType") val aiType: Int = 1,
    val width: Int = 0,
    val height: Int = 0,
)

interface PixivApi {

    /** 单个作品的全部分页。匿名可看的作品直接返回；登录墙作品 error=true */
    @GET("ajax/illust/{illustId}/pages")
    suspend fun illustPages(@Path("illustId") illustId: Long): PixivPagesResponse

    /** 插图详情（简介/标签）。匿名可看的作品直接返回；登录墙作品 error=true */
    @GET("ajax/illust/{illustId}")
    suspend fun illustDetail(@Path("illustId") illustId: Long): PixivIllustResponse

    @GET("ajax/novel/{novelId}")
    suspend fun novelMeta(@Path("novelId") novelId: Long): PixivNovelAjaxResponse


    /**
     * 作品页底部的相关作品。匿名可用（接口文档标注需要登录，但匿名照样返回）。
     * limit 上限 180，且 180 就是整个推荐池：limit + nextIds 的总量实测从不超 180
     * （2026-10 抽 15 个作品，19~180 不等），181 起直接回 400。没有翻页参数
     * （offset/page/p 都被忽略、recommend?page=1 回 404），网页端自己的「更多」也只是
     * 拿 nextIds 分批换卡片。想拿全就一次把 limit 给足。
     */
    @GET("ajax/illust/{illustId}/recommend/init")
    suspend fun recommend(
        @Path("illustId") illustId: Long,
        @Query("limit") limit: Int = PixivApiConfig.RECOMMEND_LIMIT,
    ): PixivRecommendResponse

    /**
     * 相关作品的续页：把首屏剩下的 nextIds 换成卡片，与首屏同形。
     * 网页端自己翻页也是走这里（只有池子超过首屏上限时才会用上）。
     */
    @GET("ajax/illust/recommend/illusts")
    suspend fun recommendByIds(
        @Query("illust_ids[]") illustIds: List<String>,
    ): PixivRecommendResponse

    /**
     * 网页端检索。三个端点（[PixivApiConfig.SEARCH_ARTWORKS]/[PixivApiConfig.SEARCH_ILLUST]/
     * [PixivApiConfig.SEARCH_MANGA]）参数同构、只是桶不同；`type`/`mode` 是死参数（服务端忽略），
     * 类型必须靠换端点。
     *
     * 匿名实测（2026-11）：
     * - `scd`/`ecd`（投稿日区间，yyyy-MM-dd）生效；旧参数 `duration` 已被忽略
     * - `ai_type=1` 排除 AI 生效（`ai_type=0`/缺省 = 不过滤，`ai_type=2` 回 400）
     * - `s_mode`：s_tag = 标签部分一致，s_tag_full = 标签完全一致（旧的 s_tag_exact 已失效），
     *   s_tc = 标题/简介
     * - `order`：date_d 新着、date 旧着；popular_d 是会员功能，匿名请求被静默忽略
     */
    @GET("ajax/search/artworks/{word}")
    suspend fun searchArtworks(
        @Path("word") word: String,
        @Query("word") wordQuery: String = word,
        @Query("p") page: Int,
        @Query("s_mode") sMode: String,
        @Query("order") order: String,
        @Query("scd") startDate: String? = null,
        @Query("ecd") endDate: String? = null,
        @Query("ai_type") aiType: String? = null,
    ): PixivSearchResponse

    /** 插画（含动图）检索；漫画档换这里，见 [searchArtworks] 的参数说明 */
    @GET("ajax/search/illustrations/{word}")
    suspend fun searchIllustrations(
        @Path("word") word: String,
        @Query("word") wordQuery: String = word,
        @Query("p") page: Int,
        @Query("s_mode") sMode: String,
        @Query("order") order: String,
        @Query("scd") startDate: String? = null,
        @Query("ecd") endDate: String? = null,
        @Query("ai_type") aiType: String? = null,
    ): PixivSearchResponse

    /** 漫画检索：只回 illustType=1，见 [searchArtworks] 的参数说明 */
    @GET("ajax/search/manga/{word}")
    suspend fun searchManga(
        @Path("word") word: String,
        @Query("word") wordQuery: String = word,
        @Query("p") page: Int,
        @Query("s_mode") sMode: String,
        @Query("order") order: String,
        @Query("scd") startDate: String? = null,
        @Query("ecd") endDate: String? = null,
        @Query("ai_type") aiType: String? = null,
    ): PixivSearchResponse

    @GET("ranking.php")
    suspend fun ranking(
        @Query("mode") mode: String,
        @Query("content") content: String,
        @Query("p") page: Int,
        @Query("format") format: String = "json",
    ): PixivRankingResponse
}
