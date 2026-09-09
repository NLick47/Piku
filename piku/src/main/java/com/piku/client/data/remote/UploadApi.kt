package com.piku.client.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class CreateWorkResponse(
    val content_id: Long = 0,
    val open_id: Long = 0,
    val deliver_request_result: Boolean? = null,
)

@Serializable
data class UploadImageResponse(
    val content_id: Long = 0,
    val append_id: Long = 0,
    val success: Boolean = false,
    val reset: Boolean = false,
)

/**
 * 发布接口。Step1/小说/删除都是 x-www-form-urlencoded，图片是 multipart，
 * 统一用 [@Body] [RequestBody] 由 [com.piku.client.data.repository.UploadFormBuilder] 构建，
 * 返回原始响应体便于识别"会话失效回登录页"这类非 JSON 结果。
 *
 * 独立 upload client（见 NetworkModule @Named("upload")）：120s 读写超时 + X-Requested-With。
 */
interface UploadApi {

    /** Step1：创建图片作品条目，成功返回 content_id / open_id */
    @POST("f/UploadFileRefTwitterV2F.jsp")
    suspend fun createImageWork(@Body body: RequestBody): Response<ResponseBody>

    /** 第一张图（后续走 append） */
    @POST("f/UploadFileFirstV2F.jsp")
    suspend fun uploadFirstImage(@Body body: RequestBody): Response<ResponseBody>

    /** 第二张起逐张追加 */
    @POST("f/UploadFileAppendV2F.jsp")
    suspend fun uploadAppendImage(@Body body: RequestBody): Response<ResponseBody>

    /** 小说单步发布 */
    @POST("f/UploadTextRefTwitterV2F.jsp")
    suspend fun createNovelWork(@Body body: RequestBody): Response<ResponseBody>

    /** 删除作品（中途放弃的半成品），成功 body 为纯文本 true */
    @POST("f/DeleteContentF.jsp")
    suspend fun deleteContent(@Body body: RequestBody): Response<ResponseBody>
}
