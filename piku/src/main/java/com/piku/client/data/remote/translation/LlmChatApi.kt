package com.piku.client.data.remote.translation

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import kotlinx.serialization.json.JsonObject

/**
 * OpenAI 兼容的对话补全接口。
 *
 * 用 [Url] 传完整地址而非 Retrofit baseUrl：服务地址由用户在设置里随时改（GLM /
 * DeepSeek / 自建），不能在构建 Retrofit 时固定。Authorization 也逐次传入，
 * 避免把 key 放进全局拦截器（那样会跟着别的请求泄漏出去）。
 */
interface LlmChatApi {

    @POST
    suspend fun chat(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body body: JsonObject,
    ): ChatResponse
}
