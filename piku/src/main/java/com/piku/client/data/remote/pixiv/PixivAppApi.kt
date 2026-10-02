package com.piku.client.data.remote.pixiv

import com.piku.client.data.auth.PixivAuthEndpoints
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

object PixivAppConfig {

    const val BASE_URL = "https://app-api.pixiv.net/"

    /** app-api 列表流一页多少条：推荐与关注单次上限都是 30 */
    const val PAGE_SIZE = 30

    /** 收藏/关注的可见性：public 正常，private 私密收藏 / 悄悄关注 */
    const val RESTRICT_PUBLIC = "public"
    const val RESTRICT_PRIVATE = "private"

    /** 画师作品列表的类型维度 */
    const val TYPE_ILLUST = "illust"
    const val TYPE_MANGA = "manga"

    /**
     * 内容过滤档。for_android 是限制最少的一档，R-18 是否显示交给本地开关过滤；
     * 换成 for_ios 会被服务端直接剃掉 R-18，本地开关就再也开不出来了。
     */
    const val FILTER_ANDROID = "for_android"

    /** 小说正文 webview 的版本水位：沿用 pixiv 客户端在用的值，换值可能拿不到正文 */
    const val NOVEL_VIEWER_VERSION = "20221031_ai"
}

/** 应用接口域共用的身份头；注解参数须是编译期常量，逐条 const 串起 */
private const val HEADER_USER_AGENT = "User-Agent: " + PixivAuthEndpoints.APP_USER_AGENT
private const val HEADER_APP_OS = "App-OS: " + PixivAuthEndpoints.APP_OS
private const val HEADER_APP_OS_VERSION = "App-OS-Version: " + PixivAuthEndpoints.APP_OS_VERSION
private const val HEADER_APP_VERSION = "App-Version: " + PixivAuthEndpoints.APP_VERSION

@Serializable
data class PixivIllustsResponse(
    val illusts: List<PixivAppIllust> = emptyList(),
    @SerialName("next_url") val nextUrl: String? = null,
)

/** 一件作品（app-api 版）。只取成卡用得到的字段 */
@Serializable
data class PixivAppIllust(
    @Serializable(with = FlexibleStringSerializer::class) val id: String = "",
    val title: String = "",
    @SerialName("image_urls") val imageUrls: PixivAppImageUrls = PixivAppImageUrls(),
    val user: PixivAppUser = PixivAppUser(),
    @SerialName("page_count") val pageCount: Int = 1,
    val width: Int = 0,
    val height: Int = 0,
    /** 0=全年龄 1=R-18 2=R-18G */
    @SerialName("x_restrict") val xRestrict: Int = 0,
    @SerialName("illust_ai_type") val illustAiType: Int = 0,
) {
    val illustId: Long get() = id.toLongOrNull() ?: 0
}

@Serializable
data class PixivAppImageUrls(
    @SerialName("square_medium") val squareMedium: String = "",
    val medium: String = "",
    val large: String = "",
)

@Serializable
data class PixivAppUser(
    @Serializable(with = FlexibleStringSerializer::class) val id: String = "",
    val name: String = "",
    val account: String = "",
    /** 画师自述（简介）：仅 user/detail 返回，作品内嵌的 user 无此字段 */
    val comment: String = "",
    @SerialName("profile_image_urls") val profileImageUrls: PixivAppProfileImages = PixivAppProfileImages(),
    /** 仅用户搜索等登录态接口返回；作品内嵌的 user 无此字段，默认 false */
    @SerialName("is_followed") val isFollowed: Boolean = false,
) {
    val userId: Long get() = id.toLongOrNull() ?: 0
}

@Serializable
data class PixivAppProfileImages(val medium: String = "")

/**
 * 登录态下的作品状态回显：关注/收藏操作前后各查一次的依据。
 * app-api 的 illust detail 把「我是否已收藏」「我是否已关注作者」跟作品一起带回。
 */
@Serializable
data class PixivAppIllustDetailResponse(
    val illust: PixivAppIllustState = PixivAppIllustState(),
)

/** v1/illust/detail 全量模型：详情页的分页图址/简介/标签/统计都从这里出（登录态可用） */
@Serializable
data class PixivAppIllustFullResponse(
    val illust: PixivAppIllustFull = PixivAppIllustFull(),
    /** 200 但带 error 块的拒绝（受限作品等）：不能当成成功解析 */
    val error: PixivAppErrorBody? = null,
)

@Serializable
data class PixivAppIllustFull(
    @Serializable(with = FlexibleStringSerializer::class) val id: String = "",
    val title: String = "",
    val caption: String = "",
    @SerialName("image_urls") val imageUrls: PixivAppImageUrls = PixivAppImageUrls(),
    val user: PixivAppUser = PixivAppUser(),
    @SerialName("page_count") val pageCount: Int = 1,
    val width: Int = 0,
    val height: Int = 0,
    val visible: Boolean = true,
    @SerialName("meta_single_page") val metaSinglePage: PixivAppMetaSinglePage = PixivAppMetaSinglePage(),
    @SerialName("meta_pages") val metaPages: List<PixivAppMetaPage> = emptyList(),
    val tags: List<PixivAppIllustTag> = emptyList(),
    @SerialName("total_view") val totalView: Int = 0,
    @SerialName("total_bookmarks") val totalBookmarks: Int = 0,
    @SerialName("create_date") val createDate: String = "",
)

/** 单页作品的原图在 meta_single_page（此时 meta_pages 为空） */
@Serializable
data class PixivAppMetaSinglePage(
    @SerialName("original_image_url") val originalImageUrl: String = "",
)

@Serializable
data class PixivAppMetaPage(
    @SerialName("image_urls") val imageUrls: PixivAppImageUrls = PixivAppImageUrls(),
    @SerialName("original_image_url") val originalImageUrl: String = "",
)

@Serializable
data class PixivAppIllustTag(val tag: String = "")

@Serializable
data class PixivAppIllustState(
    @Serializable(with = FlexibleStringSerializer::class) val id: String = "",
    /** 私密/受限作品为 false：状态字段不可信，按查不到处理 */
    val visible: Boolean = true,
    @SerialName("is_bookmarked") val isBookmarked: Boolean = false,
    val user: PixivAppUserState = PixivAppUserState(),
)

@Serializable
data class PixivAppUserState(
    @Serializable(with = FlexibleStringSerializer::class) val id: String = "",
    val name: String = "",
    @SerialName("is_followed") val isFollowed: Boolean = false,
)

/** 写操作（关注/收藏）的应答：成功通常是空壳，被拒时带 error 块 */
@Serializable
data class PixivAppActionResponse(
    val error: PixivAppErrorBody? = null,
    @SerialName("is_bookmarked") val isBookmarked: Boolean? = null,
)

@Serializable
data class PixivAppErrorBody(
    val message: String = "",
    @SerialName("user_message") val userMessage: String = "",
    val reason: String = "",
)

@Serializable
data class PixivAutoWordsResponse(val tags: List<PixivAutoTag> = emptyList())

@Serializable
data class PixivAutoTag(
    val name: String = "",
    @SerialName("translated_name") val translatedName: String? = null,
)

/** v1/trending-tags/illust 的热门标签，附代表作 */
@Serializable
data class PixivTrendTagsResponse(
    @SerialName("trend_tags") val trendTags: List<PixivTrendTag> = emptyList(),
)

@Serializable
data class PixivTrendTag(
    val tag: String = "",
    @SerialName("translated_name") val translatedName: String? = null,
    val illust: PixivTrendIllust = PixivTrendIllust(),
)

@Serializable
data class PixivTrendIllust(
    @Serializable(with = FlexibleStringSerializer::class) val id: String = "",
    @SerialName("image_urls") val imageUrls: PixivAppImageUrls = PixivAppImageUrls(),
    // trending-tags 里的 illust 是完整作品对象（实测样本带尺寸），瀑布流按原比例排
    val width: Int = 0,
    val height: Int = 0,
)

/** v1/search/user 与 v1/user/following 共用：条目是用户+代表作组合。total 不总在，null = 未知 */
/** 一件小说（app-api 版）。小说没有多页图，封面走 image_urls，正文另取 */
@Serializable
data class PixivNovel(
    @Serializable(with = FlexibleStringSerializer::class) val id: String = "",
    val title: String = "",
    val caption: String = "",
    @SerialName("image_urls") val imageUrls: PixivAppImageUrls = PixivAppImageUrls(),
    val user: PixivAppUser = PixivAppUser(),
    val tags: List<PixivNovelTag> = emptyList(),
    @SerialName("create_date") val createDate: String = "",
    @SerialName("text_length") val textLength: Int = 0,
    /** 0=全年龄 1=R-18 2=R-18G */
    @SerialName("x_restrict") val xRestrict: Int = 0,
    @SerialName("total_bookmarks") val totalBookmarks: Int? = null,
    @SerialName("total_view") val totalView: Int? = null,
    @SerialName("total_comments") val totalComments: Int? = null,
    @SerialName("is_bookmarked") val isBookmarked: Boolean = false,
    val visible: Boolean = true,
    val series: PixivNovelSeries? = null,
) {
    val novelId: Long get() = id.toLongOrNull() ?: 0
}

@Serializable
data class PixivNovelTag(val name: String = "")

@Serializable
data class PixivNovelSeries(
    @Serializable(with = FlexibleStringSerializer::class) val id: String = "",
    val title: String = "",
) {
    val seriesId: Long get() = id.toLongOrNull() ?: 0
}

@Serializable
data class PixivNovelsResponse(
    val novels: List<PixivNovel> = emptyList(),
    @SerialName("next_url") val nextUrl: String? = null,
)

@Serializable
data class PixivNovelDetailResponse(val novel: PixivNovel = PixivNovel())

/** 小说正文：官方把 /v1/novel/text 摘掉了（2026-10 实测 404），只剩 webview 这条返回 HTML 的路 */
@Serializable
data class PixivWebviewNovel(
    val text: String = "",
    val title: String = "",
)

@Serializable
data class PixivUserPreviewsResponse(
    @SerialName("user_previews") val userPreviews: List<PixivUserPreview> = emptyList(),
    val total: Int? = null,
)

@Serializable
data class PixivUserPreview(
    val user: PixivAppUser = PixivAppUser(),
    val illusts: List<PixivAppIllust> = emptyList(),
)

/** 画师主页资料：user 是账号主体，profile 是资料区的统计与外链 */
@Serializable
data class PixivUserDetailResponse(
    val user: PixivAppUser = PixivAppUser(),
    val profile: PixivUserProfile = PixivUserProfile(),
)

/**
 * 只取画师主页资料区用得上的字段。计数一律可空：字段缺失与「真的是 0」在界面上要能分开
 * （粉丝数尤其——2026-10-01 真机确认 app-api 压根不返回 `total_follower`，
 * 这个字段留着是给字段名留档，等哪天回来了再说；界面上不要用它）。
 */
@Serializable
data class PixivUserProfile(
    val webpage: String? = null,
    @SerialName("total_follow_users") val totalFollowUsers: Int? = null,
    @SerialName("total_follower") val totalFollower: Int? = null,
    @SerialName("total_illusts") val totalIllusts: Int? = null,
    @SerialName("total_manga") val totalManga: Int? = null,
    @SerialName("total_novels") val totalNovels: Int? = null,
    /** 公开收藏数：收藏 Tab 的计数就是它（私密收藏数不对外） */
    @SerialName("total_illust_bookmarks_public") val totalIllustBookmarksPublic: Int? = null,
    @SerialName("background_image_url") val backgroundImageUrl: String? = null,
    @SerialName("twitter_account") val twitterAccount: String = "",
    @SerialName("twitter_url") val twitterUrl: String? = null,
    @SerialName("is_premium") val isPremium: Boolean = false,
)

interface PixivAppApi {

    // 该域须带应用身份 浏览器 UA 会被拒 Bearer 由传输层按主机补
    // 两个签名头须同一时间串 故由调用方整对传入
    @GET("v1/illust/recommended")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun recommended(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("content_type") contentType: String = "illust",
        @Query("filter") filter: String = PixivAppConfig.FILTER_ANDROID,
        @Query("include_ranking_illusts") includeRankingIllusts: Boolean = false,
        @Query("offset") offset: Int? = null,
    ): PixivIllustsResponse

    /** 关注流：已关注画师的新作，时间倒序。与推荐同为 offset 翻页，restrict=private 只看悄悄关注 */
    @GET("v2/illust/follow")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun followFeed(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("restrict") restrict: String = PixivAppConfig.RESTRICT_PUBLIC,
        @Query("filter") filter: String = PixivAppConfig.FILTER_ANDROID,
        @Query("offset") offset: Int? = null,
    ): PixivIllustsResponse

    /** 关键词搜作品。search_target/sort/duration 枚举与筛选面板声明一一对应；searchAiType 0=隐藏 AI */
    @GET("v1/search/illust")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun searchIllust(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("word") word: String,
        @Query("search_target") searchTarget: String? = null,
        @Query("sort") sort: String? = null,
        @Query("duration") duration: String? = null,
        @Query("search_ai_type") searchAiType: Int? = null,
        @Query("filter") filter: String = "for_android",
        @Query("offset") offset: Int? = null,
    ): PixivIllustsResponse

    /** 关键词搜小说；search_target 枚举与搜作品不同，外壳在小说档不暴露对象组，只传排序与 AI 过滤 */
    @GET("v1/search/novel")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun searchNovel(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("word") word: String,
        @Query("sort") sort: String? = null,
        @Query("search_ai_type") searchAiType: Int? = null,
        @Query("filter") filter: String = PixivAppConfig.FILTER_ANDROID,
        @Query("offset") offset: Int? = null,
    ): PixivNovelsResponse

    /** 关键词搜用户；登录态下 user.is_followed 有值 */
    @GET("v1/search/user")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun searchUser(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("word") word: String,
        @Query("filter") filter: String = "for_android",
        @Query("offset") offset: Int? = null,
    ): PixivUserPreviewsResponse

    @GET("v1/user/following")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun userFollowing(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("user_id") userId: Long,
        @Query("restrict") restrict: String = PixivAppConfig.RESTRICT_PUBLIC,
        @Query("offset") offset: Int? = null,
    ): PixivUserPreviewsResponse

    /** 标签联想（v2）：返回标签 + 简中译名，供输入联想层 */
    @GET("v2/search/autocomplete")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun autocomplete(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("word") word: String,
    ): PixivAutoWordsResponse

    /** 24 小时热门标签，附代表作缩略图 */
    @GET("v1/trending-tags/illust")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun trendingTags(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("filter") filter: String = "for_android",
    ): PixivTrendTagsResponse

    /**
     * 新着流：全站最新投稿，时间序。登录才可用。
     * 翻页不是 offset 而是作品 id 游标：响应 next_url 带回下一页的 max_illust_id。
     */
    @GET("v1/illust/new")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun illustNew(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("content_type") contentType: String,
        @Query("filter") filter: String = PixivAppConfig.FILTER_ANDROID,
        @Query("max_illust_id") maxIllustId: Long? = null,
    ): PixivIllustsResponse

    /** 登录用户视角的作品状态（是否已收藏、是否已关注作者）；仅登录态下有意义 */
    @GET("v1/illust/detail")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun illustState(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("illust_id") illustId: Long,
    ): PixivAppIllustDetailResponse

    /** 作品全量详情：各页图址/简介/标签/统计；登录限定作品只有这条路拿得到。需登录 */
    @GET("v1/illust/detail")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun illustDetail(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("illust_id") illustId: Long,
        @Query("filter") filter: String = PixivAppConfig.FILTER_ANDROID,
    ): PixivAppIllustFullResponse

    /** 关注作者；restrict=private 即悄悄关注 */
    @POST("v1/user/follow/add")
    @FormUrlEncoded
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun followAdd(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Field("user_id") userId: Long,
        @Field("restrict") restrict: String = PixivAppConfig.RESTRICT_PUBLIC,
    ): PixivAppActionResponse

    @POST("v1/user/follow/delete")
    @FormUrlEncoded
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun followDelete(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Field("user_id") userId: Long,
    ): PixivAppActionResponse

    /** 加入收藏；restrict=private 即私密收藏。标签收藏不在此列（本地收藏夹管组织） */
    @POST("v2/illust/bookmark/add")
    @FormUrlEncoded
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun bookmarkAdd(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Field("illust_id") illustId: Long,
        @Field("restrict") restrict: String = PixivAppConfig.RESTRICT_PUBLIC,
    ): PixivAppActionResponse

    @POST("v1/illust/bookmark/delete")
    @FormUrlEncoded
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun bookmarkDelete(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Field("illust_id") illustId: Long,
    ): PixivAppActionResponse

    /** 画师主页资料（简介/统计/外链）。需登录 */
    @GET("v1/user/detail")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun userDetail(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("user_id") userId: Long,
        @Query("filter") filter: String = PixivAppConfig.FILTER_ANDROID,
    ): PixivUserDetailResponse

    /** 画师的作品，按类型分池：插画与漫画分两次拉，各自 offset 翻页 */
    @GET("v1/user/illusts")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun userIllusts(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("user_id") userId: Long,
        @Query("type") type: String,
        @Query("filter") filter: String = PixivAppConfig.FILTER_ANDROID,
        @Query("offset") offset: Int? = null,
    ): PixivIllustsResponse

    /** 画师的小说，offset 翻页；下一页看 next_url，与 user/illusts 同型 */
    @GET("v1/user/novels")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun userNovels(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("user_id") userId: Long,
        @Query("filter") filter: String = PixivAppConfig.FILTER_ANDROID,
        @Query("offset") offset: Int? = null,
    ): PixivNovelsResponse

    /**
     * 别人的公开收藏（自己的能带私密，本应用只用公开）。
     * 翻页游标是 max_bookmark_id 而不是 offset，与其它列表不同型。
     */
    @GET("v1/user/bookmarks/illust")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun userBookmarks(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("user_id") userId: Long,
        @Query("restrict") restrict: String = PixivAppConfig.RESTRICT_PUBLIC,
        @Query("max_bookmark_id") maxBookmarkId: Long? = null,
        @Query("filter") filter: String = PixivAppConfig.FILTER_ANDROID,
    ): PixivIllustsResponse

    /** 推荐小说；与插画推荐同为 offset 翻页，需登录 */
    @GET("v1/novel/recommended")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun novelRecommended(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("filter") filter: String = PixivAppConfig.FILTER_ANDROID,
        @Query("include_ranking_label") includeRankingLabel: Boolean = false,
        @Query("offset") offset: Int? = null,
    ): PixivNovelsResponse

    /** 已关注作者的新小说，时间倒序，offset 翻页。需登录 */
    @GET("v1/novel/follow")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun novelFollow(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("restrict") restrict: String = PixivAppConfig.RESTRICT_PUBLIC,
        @Query("offset") offset: Int? = null,
    ): PixivNovelsResponse

    /** 新着小说：全站最新，翻页靠 max_novel_id 游标而不是 offset */
    @GET("v1/novel/new")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun novelNew(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("filter") filter: String = PixivAppConfig.FILTER_ANDROID,
        @Query("max_novel_id") maxNovelId: Long? = null,
    ): PixivNovelsResponse

    /** 小说详情（简介/标签/统计/系列）。v2 比 v1 多 series_prev/next 信息 */
    @GET("v2/novel/detail")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun novelDetail(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("novel_id") novelId: Long,
    ): PixivNovelDetailResponse

    /**
     * 小说正文。官方的 /v1/novel/text 已下线（实测 404），现在只有这条 webview：
     * 回的是 HTML，正文藏在页面里 `novel: {...}` 这个 JS 对象里。
     */
    @GET("webview/v2/novel")
    @Headers(HEADER_USER_AGENT, HEADER_APP_OS, HEADER_APP_OS_VERSION, HEADER_APP_VERSION)
    suspend fun novelWebview(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("id") novelId: Long,
        @Query("viewer_version") viewerVersion: String = PixivAppConfig.NOVEL_VIEWER_VERSION,
    ): ResponseBody
}
