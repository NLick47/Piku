package com.piku.client.data.remote.translation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 翻译请求体改为在引擎内用 JsonObject 动态拼装（参数名来自远程目录，不写死），故不再有固定 ChatRequest。 */

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatResponse(
    val choices: List<ChatChoice> = emptyList(),
) {
    /** 首个候选的正文，缺失时为空串（由调用方判定为失败并重试） */
    val content: String
        get() = choices.firstOrNull()?.message?.content.orEmpty()
}

@Serializable
data class ChatChoice(
    val message: ChatMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)
