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

/** result：1=屏蔽成功，2=解除屏蔽成功，其余为失败 */
@Serializable
data class UpdateBlockUserResponse(val result: Int = 0)

@Serializable
data class TagSuggestionResponse(
    val result: Int = 0,
    val input: String = "",
    val tags: List<String> = emptyList(),
)

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

    @GET("MyEditSettingPcV.jsp")
    suspend fun getFollowSettingPage(
        @Query("MENUID") menuId: String,
    ): ResponseBody

    @FormUrlEncoded
    @POST("f/FollowListF.jsp")
    suspend fun getFollowList(
        @Field("MAX") max: Int,
        @Field("MD") md: Int,
        @Field("PG") page: Int,
    ): ResponseBody

    @GET("IllustListPcV.jsp")
    suspend fun getUserIllusts(
        @Query("ID") userId: Long,
        @Query("KWD") keyword: String,
        @Query("PG") page: Int,
    ): ResponseBody

    @GET("PopularTagListPcV.jsp")
    suspend fun getPopularTags(): ResponseBody

    @GET("SearchIllustByTagPcV.jsp")
    suspend fun getTagSearch(
        @Query("KWD") keyword: String,
        @Query("PG") page: Int,
    ): ResponseBody

    /** 标签建议（需登录；未登录时服务端返回首页壳） */
    @GET("SearchTagByKeywordPcV.jsp")
    suspend fun getTagSuggestions(
        @Query("KWD") keyword: String,
        @Query("PG") page: Int,
    ): ResponseBody

    @GET("SearchIllustByKeywordPcV.jsp")
    suspend fun getKeywordSearch(
        @Query("KWD") keyword: String,
        @Query("PG") page: Int,
    ): ResponseBody

    /** 作者搜索（需登录；匿名访问服务端返回 404/登录页，调用前须由业务层先行拦截） */
    @GET("SearchUserByKeywordPcV.jsp")
    suspend fun getUserSearch(
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

    @FormUrlEncoded
    @POST("f/UpdateFollowUserF.jsp")
    suspend fun updateFollowUser(
        @Field("UID") uid: Long,
        @Field("IID") targetUserId: Long,
    ): UpdateFollowUserResponse

    /**
     * 屏蔽/解除屏蔽用户（网页端作品页与用户主页的 UserInfoCmdBlock 同款请求）。
     * UID 为当前登录用户，IID 为目标用户，CHK 1=屏蔽 0=解除；
     * 服务端会在屏蔽成功时同时解除对该用户的关注。
     */
    @FormUrlEncoded
    @POST("f/UpdateBlockF.jsp")
    suspend fun updateBlockUser(
        @Field("UID") uid: Long,
        @Field("IID") targetUserId: Long,
        @Field("CHK") checked: Int,
    ): UpdateBlockUserResponse

    /**
     * 屏蔽列表（设置页「ブロックリスト」同款请求，MD 固定 1）。
     * 返回结构与 FollowListF 一致（`<a class="UserInfo Thumb" href="/{uid}/">` 列表），
     * 但不带 TOTAL 分页信息：列表为空即表示已到末页。
     */
    @FormUrlEncoded
    @POST("f/BlockListF.jsp")
    suspend fun getBlockList(
        @Field("MAX") max: Int,
        @Field("MD") md: Int,
        @Field("PG") page: Int,
    ): ResponseBody

    @GET("f/GetTagSuggestionF.jsp")
    suspend fun getTagAutoComplete(
        @Query("type") type: String = "tag",
        @Query("input") input: String,
    ): TagSuggestionResponse

    /**
     * 图集编辑页（服务端预填作品当前值，是编辑表单的回填数据源）
     * 注意：该 URL 不校验作品类型，TD 传小说 ID 也会返回图集编辑页壳——
     * 调用前必须先经详情页判定类型。
     */
    @GET("UpdateFilePcV2.jsp")
    suspend fun getIllustEditPage(
        @Query("ID") userId: Long,
        @Query("TD") workId: Long,
    ): ResponseBody

    @GET("UpdateTextPcV2.jsp")
    suspend fun getNovelEditPage(
        @Query("ID") userId: Long,
        @Query("TD") workId: Long,
    ): ResponseBody
}