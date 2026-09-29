package com.piku.client.data.remote.pixiv

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

object PixivApiConfig {

    const val BASE_URL = "https://www.pixiv.net/"

    const val PAGE_SIZE = 50

    /** 相关作品一次取多少：够铺两屏，再多就是白拉流量 */
    const val RECOMMEND_LIMIT = 18

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
 * 只取详情页用得到的字段。计数类（view/like/bookmark/comment）在匿名访问下也在，
 * 但个别作品（限制公开、接口降级）会缺，缺了按默认值 0 处理——UI 只展示有意义的项。
 */
@Serializable
data class PixivIllustBody(
    val description: String = "",
    val tags: PixivIllustTags = PixivIllustTags(),
    @SerialName("viewCount") val viewCount: Int = 0,
    @SerialName("likeCount") val likeCount: Int = 0,
    @SerialName("bookmarkCount") val bookmarkCount: Int = 0,
    @SerialName("commentCount") val commentCount: Int = 0,
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
data class PixivRecommendBody(
    val illusts: List<PixivWorkCard> = emptyList(),
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

interface PixivApi {

    /** 单个作品的全部分页。匿名可看的作品直接返回；登录墙作品 error=true */
    @GET("ajax/illust/{illustId}/pages")
    suspend fun illustPages(@Path("illustId") illustId: Long): PixivPagesResponse

    /** 插图详情（简介/标签）。匿名可看的作品直接返回；登录墙作品 error=true */
    @GET("ajax/illust/{illustId}")
    suspend fun illustDetail(@Path("illustId") illustId: Long): PixivIllustResponse


    /**
     * 作品页底部的相关作品。实测匿名可用（2026-09：HTTP 200、error=false、18 条），
     * 接口文档标注需要登录，但匿名照样返回。nextIds 供翻页，这里只取首屏。
     */
    @GET("ajax/illust/{illustId}/recommend/init")
    suspend fun recommend(
        @Path("illustId") illustId: Long,
        @Query("limit") limit: Int = PixivApiConfig.RECOMMEND_LIMIT,
    ): PixivRecommendResponse

    @GET("ranking.php")
    suspend fun ranking(
        @Query("mode") mode: String,
        @Query("content") content: String,
        @Query("p") page: Int,
        @Query("format") format: String = "json",
    ): PixivRankingResponse
}
