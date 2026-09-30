package com.piku.client.data.auth

import com.piku.client.data.remote.pixiv.FlexibleStringSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

@Serializable
data class PixivTokenResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    /** 有效期秒数，通常 3600 */
    @SerialName("expires_in") val expiresIn: Long = 0,
    val user: PixivAccount = PixivAccount(),
    val error: String = "",
)

/** 账号信息。id 有时是字符串有时是数字，按宽松串解析 */
@Serializable
data class PixivAccount(
    @Serializable(with = FlexibleStringSerializer::class) val id: String = "",
    val name: String = "",
    val account: String = "",
    @SerialName("profile_image_urls") val profileImageUrls: PixivProfileImages = PixivProfileImages(),
)

@Serializable
data class PixivProfileImages(
    @SerialName("px_170x170") val px170: String = "",
)

interface PixivAuthApi {

    /**
     * 一只脚踩两用的端点：授权码换令牌与刷新令牌都打这里，
     * 靠 grant_type 区分（字段构造见 [PixivAuthEndpoints]）。
     *
     * 带**应用客户端身份**：这个端点是给 App 用的，浏览器 UA 会被当成非法客户端；
     * 而 [PixivAuthEndpoints.clientSignature] 产出的两个签名头是**必填**的，
     * 缺任一 pixiv 都回 1508「客户端凭据不合法」。
     *
     * 返回 `Response` 而不是 DTO，是为了拿到非 2xx 的状态码与错误正文——
     * 失败原因（invalid_grant / invalid_client / 被挡）全在那里面，丢掉就只剩一句"登录失败"。
     */
    @FormUrlEncoded
    @POST("auth/token")
    @Headers(
        "User-Agent: " + PixivAuthEndpoints.APP_USER_AGENT,
        "App-OS: " + PixivAuthEndpoints.APP_OS,
        "App-OS-Version: " + PixivAuthEndpoints.APP_OS_VERSION,
        "App-Version: " + PixivAuthEndpoints.APP_VERSION,
    )
    suspend fun token(
        @FieldMap fields: Map<String, String>,
        @Header("X-Client-Time") clientTime: String,
        @Header("X-Client-Hash") clientHash: String,
    ): Response<PixivTokenResponse>
}
