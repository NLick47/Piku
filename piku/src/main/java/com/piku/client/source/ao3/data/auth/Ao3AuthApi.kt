package com.piku.client.source.ao3.data.auth

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * AO3 的会话登录。站点没有 API，走 Rails 的表单：先取登录页上的 csrf-token，
 * 再按 `user[login]/user[password]` 提交。成功与失败都是 302，靠 Location 区分，
 * 所以这条链的 client 关闭了重定向跟随。
 */
interface Ao3AuthApi {

    @GET("users/login")
    suspend fun loginPage(): Response<ResponseBody>

    @FormUrlEncoded
    @POST("users/login")
    suspend fun login(
        @Field("authenticity_token") token: String,
        @Field("user[login]") login: String,
        @Field("user[password]") password: String,
        @Field("commit") commit: String,
        /** 勾上 Remember Me：cookie 有效期一年，等于本源自己的"保持登录" */
        @Field("user[remember_me]") rememberMe: String,
    ): Response<ResponseBody>
}
