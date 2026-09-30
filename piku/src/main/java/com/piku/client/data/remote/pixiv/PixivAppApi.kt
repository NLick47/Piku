package com.piku.client.data.remote.pixiv

import com.piku.client.data.auth.PixivAuthEndpoints
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Query

object PixivAppConfig {

    const val BASE_URL = "https://app-api.pixiv.net/"

    /** 推荐流一页多少条：接口单次上限 30 */
    const val PAGE_SIZE = 30
}

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
) {
    val userId: Long get() = id.toLongOrNull() ?: 0
}

@Serializable
data class PixivAppProfileImages(val medium: String = "")

interface PixivAppApi {

    // 该域须带应用身份 浏览器 UA 会被拒 Bearer 由传输层按主机补
    // 两个签名头须同一时间串 故由调用方整对传入
    @GET("v1/illust/recommended")
    @Headers(
        "User-Agent: " + PixivAuthEndpoints.APP_USER_AGENT,
        "App-OS: " + PixivAuthEndpoints.APP_OS,
        "App-OS-Version: " + PixivAuthEndpoints.APP_OS_VERSION,
        "App-Version: " + PixivAuthEndpoints.APP_VERSION,
    )
    suspend fun recommended(
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
        @Query("content_type") contentType: String = "illust",
        @Query("filter") filter: String = "for_android",
        @Query("include_ranking_illusts") includeRankingIllusts: Boolean = false,
        @Query("offset") offset: Int? = null,
    ): PixivIllustsResponse
}
