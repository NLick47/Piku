package com.piku.client.data.remote

import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

object ApiConfig {
    const val BASE_URL = "https://poipiku.com/"
}

@Serializable
data class SwitchContentsViewModeResponse(val result: Int = -1)

@Serializable
data class AppendFileResponse(val result_num: Int = 0, val html: String = "")

@Serializable
data class ShowIllustDetailResponse(val result: Int = 0, val html: String = "", val error_code: Int = 0)

@Serializable
data class SendEmojiResponse(val result_num: Int = 0, val result: String = "", val error_code: Int = 0)

@Serializable
data class UpdateFollowUserResponse(val result: Int = 0, val btn_label: String = "", val err_msg: String = "")

interface PoipikuApi {

    @GET("NewArrivalPcV.jsp")
    suspend fun getNewArrivals(
        @Query("PG") page: Int,
        @Query("CD") categoryCd: Int,
    ): ResponseBody

    @GET("PopularIllustListPcV.jsp")
    suspend fun getPopularIllusts(
        @Query("PG") page: Int,
    ): ResponseBody

    @GET("RandomPickupPcV.jsp")
    suspend fun getRandomPickup(): ResponseBody

    /** 关注时间线（需登录；未登录时服务端返回登录页而不是关注页） */
    @GET("MyHomePcV.jsp")
    suspend fun getFollowFeed(
        @Query("PG") page: Int,
    ): ResponseBody

    @GET("PopularTagListPcV.jsp")
    suspend fun getPopularTags(): ResponseBody

    @GET("SearchIllustByTagPcV.jsp")
    suspend fun getTagSearch(
        @Query("KWD") keyword: String,
        @Query("PG") page: Int,
    ): ResponseBody

    @GET("SearchIllustByKeywordPcV.jsp")
    suspend fun getKeywordSearch(
        @Query("KWD") keyword: String,
        @Query("PG") page: Int,
    ): ResponseBody

    @GET("{userId}/{workId}.html")
    suspend fun getWorkDetail(
        @Path("userId") userId: Long,
        @Path("workId") workId: Long,
    ): ResponseBody

    @FormUrlEncoded
    @POST("f/ShowAppendFileF.jsp")
    suspend fun showAppendFile(
        @Field("UID") userId: Long,
        @Field("IID") workId: Long,
        @Field("PAS") pas: String,
        @Field("MD") md: Int,
        @Field("TWF") twf: Int,
    ): AppendFileResponse

    @FormUrlEncoded
    @POST("f/ShowIllustDetailF.jsp")
    suspend fun showIllustDetail(
        @Field("ID") userId: Long,
        @Field("TD") workId: Long,
        @Field("AD") appendIndex: Int,
        @Field("PAS") pas: String,
    ): ShowIllustDetailResponse

    @FormUrlEncoded
    @POST("f/SwitchContentsViewModeF.jsp")
    suspend fun switchContentsViewMode(
        @Field("MD") md: Int,
    ): SwitchContentsViewModeResponse

    @FormUrlEncoded
    @POST("f/SendEmojiF.jsp")
    suspend fun sendEmoji(
        @Field("IID") workId: Long,
        @Field("EMJ") emoji: String,
        @Field("UID") userId: Long,
    ): SendEmojiResponse

    /**
     * 关注/取消关注用户（详情页"フォロー"按钮同款接口，Web 端由
     * UpdateFollowUser(-1, {authorId}) 触发；登录后第一个参数是当前用户 ID）。
     * result=1 已关注、2 已取消关注、其余失败（err_msg 带原因）。
     */
    @FormUrlEncoded
    @POST("f/UpdateFollowUserF.jsp")
    suspend fun updateFollowUser(
        @Field("UID") uid: Long,
        @Field("IID") targetUserId: Long,
    ): UpdateFollowUserResponse
}