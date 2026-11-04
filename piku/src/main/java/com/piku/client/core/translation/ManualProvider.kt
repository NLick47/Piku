package com.piku.client.core.translation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
enum class AuthMode {
    NONE,
    BEARER,
    HEADER,
}

@Serializable
data class ManualProvider(
    val id: String = "",
    val name: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val authMode: AuthMode = AuthMode.NONE,
    val authHeader: String = DEFAULT_AUTH_HEADER,
    val roles: List<String> = listOf(Role.TEXT),
    val baseUrls: List<String> = emptyList(),
    val extraHeaders: Map<String, String> = emptyMap(),
    val extraParams: Map<String, JsonElement> = emptyMap(),
    val hasKey: Boolean = false,
    val promptOverrides: PromptOverrides = PromptOverrides(),
    val createdAt: Long = 0L,
) {
    fun toModelEntry(apiKey: String?): ModelEntry = ModelEntry(
        id = id,
        label = name.ifBlank { baseUrl },
        baseUrl = baseUrl,
        model = model,
        free = false,
        verified = false,
        available = true,
        apiKey = apiKey?.takeIf { it.isNotBlank() && authMode != AuthMode.NONE },
        hint = "",
        params = extraParams,
        roles = roles,
        baseUrls = baseUrls,
        authMode = authMode,
        authHeader = authHeader,
        extraHeaders = extraHeaders,
        prompts = promptOverrides.toPromptSet(),
        isManual = true,
    )

    companion object {
        const val DEFAULT_AUTH_HEADER = "X-API-Key"
    }
}

fun llmAuthHeaders(
    authMode: AuthMode,
    authHeader: String,
    apiKey: String,
    extraHeaders: Map<String, String> = emptyMap(),
): Map<String, String> {
    val auth = when (authMode) {
        AuthMode.NONE -> emptyMap()
        AuthMode.BEARER -> mapOf("Authorization" to "Bearer $apiKey")
        AuthMode.HEADER -> mapOf(
            authHeader.ifBlank { ManualProvider.DEFAULT_AUTH_HEADER } to apiKey,
        )
    }
    return extraHeaders + auth
}
