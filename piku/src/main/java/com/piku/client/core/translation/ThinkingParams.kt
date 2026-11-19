package com.piku.client.core.translation

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

enum class ThinkingParam {
    ENABLE_THINKING,
    CHAT_TEMPLATE_KWARGS,
    THINKING_OBJECT,
    ;

    companion object {
        fun fromName(value: String?): ThinkingParam? =
            entries.firstOrNull { it.name == value }
    }
}

fun thinkingOffParams(mode: ThinkingParam): Map<String, JsonElement> = when (mode) {
    ThinkingParam.ENABLE_THINKING -> mapOf("enable_thinking" to JsonPrimitive(false))
    ThinkingParam.CHAT_TEMPLATE_KWARGS -> mapOf(
        "chat_template_kwargs" to buildJsonObject { put("enable_thinking", JsonPrimitive(false)) },
    )
    ThinkingParam.THINKING_OBJECT -> mapOf(
        "thinking" to buildJsonObject { put("type", JsonPrimitive("disabled")) },
    )
}

private val THINKING_KEYS = setOf("enable_thinking", "chat_template_kwargs", "thinking")

fun inferThinkingParam(baseUrl: String, model: String): ThinkingParam {
    val url = baseUrl.lowercase()
    val name = model.lowercase()
    return when {
        url.contains("bigmodel.cn") || name.startsWith("glm") -> ThinkingParam.THINKING_OBJECT
        isLocalAddress(url) -> ThinkingParam.CHAT_TEMPLATE_KWARGS
        else -> ThinkingParam.ENABLE_THINKING
    }
}

private fun isLocalAddress(url: String): Boolean {
    val host = url.substringAfter("://", url).substringBefore('/').substringBefore(':')
    return host == "localhost" ||
        host.endsWith(".local") ||
        host.startsWith("127.") ||
        host.startsWith("10.") ||
        host.startsWith("192.168.") ||
        LOCAL_172_RANGE.find(host) != null
}

private val LOCAL_172_RANGE = Regex("""^172\.(1[6-9]|2\d|3[01])\.""")

fun applyThinking(
    params: Map<String, JsonElement>,
    thinkingEnabled: Boolean,
    mode: ThinkingParam,
    touched: Boolean,
): Map<String, JsonElement> {
    val hadThinkingField = params.keys.any { it in THINKING_KEYS }
    if (!touched && !hadThinkingField) return params
    val cleared = params - THINKING_KEYS
    if (thinkingEnabled) return cleared
    return cleared + thinkingOffParams(mode)
}

fun isThinkingEnabled(params: Map<String, JsonElement>): Boolean {
    val flag = params["enable_thinking"]
    if (flag is JsonPrimitive && !flag.isString && flag.content == "true") return true
    val nested = params["chat_template_kwargs"] as? JsonObject
    val nestedFlag = nested?.get("enable_thinking")
    if (nestedFlag is JsonPrimitive && !nestedFlag.isString && nestedFlag.content == "true") return true
    val thinking = params["thinking"] as? JsonObject ?: return false
    val type = thinking["type"] as? JsonPrimitive ?: return false
    return type.content == "enabled"
}

fun isThinkingDisabled(params: Map<String, JsonElement>): Boolean {
    val flag = params["enable_thinking"]
    if (flag is JsonPrimitive && !flag.isString && flag.content == "false") return true
    val nested = params["chat_template_kwargs"] as? JsonObject
    val nestedFlag = nested?.get("enable_thinking")
    if (nestedFlag is JsonPrimitive && !nestedFlag.isString && nestedFlag.content == "false") return true
    val thinking = params["thinking"] as? JsonObject ?: return false
    val type = thinking["type"] as? JsonPrimitive ?: return false
    return type.content == "disabled"
}

fun thinkingParamOf(params: Map<String, JsonElement>): ThinkingParam? = when {
    params.containsKey("chat_template_kwargs") -> ThinkingParam.CHAT_TEMPLATE_KWARGS
    params.containsKey("thinking") -> ThinkingParam.THINKING_OBJECT
    params.containsKey("enable_thinking") -> ThinkingParam.ENABLE_THINKING
    else -> null
}
