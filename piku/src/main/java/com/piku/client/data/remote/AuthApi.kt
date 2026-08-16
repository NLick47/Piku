package com.piku.client.data.remote

import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class LoginResponse(val result: Int = -1)

@Serializable
data class UpdateNickNameResponse(val result: Int = -1)

@Serializable
data class UpdateProfileFileResponse(val result: Int = -1)

interface AuthApi {

    @FormUrlEncoded
    @POST("f/LoginUserF.jsp")
    suspend fun login(
        @Field("EM") email: String,
        @Field("PW") password: String,
    ): LoginResponse

    @GET("MyHomePcV.jsp")
    suspend fun getMyHome(): Response<ResponseBody>

    @GET("MyEditSettingPcV.jsp")
    suspend fun getMyEditSetting(@Query("ID") id: Long): Response<ResponseBody>

    /** 公开的用户主页，用于解析用户昵称（第一个 IllustUserName 即页主） */
    @GET("{uid}/")
    suspend fun getUserTop(@Path("uid") uid: Long): Response<ResponseBody>

    /** 修改昵称（MyEditSettingPcV 的 UpdateNickName 同款请求：ID + NN） */
    @FormUrlEncoded
    @POST("f/UpdateNickNameF.jsp")
    suspend fun updateNickName(
        @Field("ID") id: Long,
        @Field("NN") name: String,
    ): UpdateNickNameResponse

    /**
     * 上传头像（网页端 MyEditSettingPcV 的 updateFile 同款：form 提交 UID + DATA）。
     * DATA 为图片字节的 base64（不含 data:image/...;base64, 前缀）。
     * result：0=成功，-1=文件过大，-2=文件类型不支持。
     */
    @FormUrlEncoded
    @POST("f/UpdateProfileFileF.jsp")
    suspend fun updateProfileFile(
        @Field("UID") uid: Long,
        @Field("DATA") dataBase64: String,
    ): UpdateProfileFileResponse
}