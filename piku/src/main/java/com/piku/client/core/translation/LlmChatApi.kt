package com.piku.client.core.translation

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Url
import kotlinx.serialization.json.JsonObject

/**
 * OpenAI 兼容的对话补全接口。
 *
 * 用 [Url] 传完整地址而非 Retrofit baseUrl：服务地址由用户在设置里随时改（GLM /
 * DeepSeek / 自建），不能在构建 Retrofit 时固定。鉴权头也逐次传入，
 * 避免把 key 放进全局拦截器（那样会跟着别的请求泄漏出去）。
 * 手动 API 还可能是无鉴权或自定义 Header（api-key / x-api-key）。
 */
interface LlmChatApi {

    @POST
    suspend fun chat(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: JsonObject,
    ): ChatResponse

    @GET
    suspend fun models(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
    ): LlmModelsResponse
}
