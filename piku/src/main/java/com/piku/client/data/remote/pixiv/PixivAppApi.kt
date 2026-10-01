package com.piku.client.data.remote.pixiv

import com.piku.client.data.auth.PixivAuthEndpoints
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

    /**
     * 内容过滤档。for_android 是限制最少的一档，R-18 是否显示交给本地开关过滤；
     * 换成 for_ios 会被服务端直接剃掉 R-18，本地开关就再也开不出来了。
     */
    const val FILTER_ANDROID = "for_android"
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

/** v2/search/autocomplete 的标签联想（字段经 PixEz 参考实现核对） */
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
}
