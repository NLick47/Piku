package com.piku.client.core.translation

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManualProviderRepository @Inject constructor(
    private val prefs: SharedPreferences,
    private val json: Json,
    private val secretStore: LlmSecretStore,
) {

    private val _providers = MutableStateFlow(load())
    val providers: StateFlow<List<ManualProvider>> = _providers.asStateFlow()

    fun find(id: String): ManualProvider? = _providers.value.firstOrNull { it.id == id }

    fun apiKey(id: String): String? = secretStore.get(id)

    fun upsert(provider: ManualProvider, newKey: String?): ManualProvider {
        val id = provider.id.ifBlank { manualId() }
        val normalizedBaseUrl = normalizeManualBaseUrl(provider.baseUrl)
        val fallbackName = runCatching { URI(normalizedBaseUrl).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "API"
        val normalized = provider.copy(
            id = id,
            name = provider.name.trim().ifBlank { fallbackName },
            baseUrl = normalizedBaseUrl,
            model = provider.model.trim(),
            authHeader = provider.authHeader.trim().ifBlank { ManualProvider.DEFAULT_AUTH_HEADER },
            roles = provider.roles.map { it.trim() }.filter { it in KNOWN_ROLES }.distinct()
                .ifEmpty { listOf(Role.TEXT) },
            baseUrls = provider.baseUrls.map { normalizeManualBaseUrl(it) }
                .filter { it.isNotBlank() }.distinct(),
            extraHeaders = provider.extraHeaders
                .mapKeys { it.key.trim() }
                .filterKeys { it.isNotEmpty() },
            promptOverrides = provider.promptOverrides.normalized(),
            createdAt = provider.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
        if (newKey != null) {
            if (newKey.isBlank()) secretStore.remove(id) else secretStore.put(id, newKey)
        }
        val saved = normalized.copy(hasKey = !secretStore.get(id).isNullOrBlank())
        persist(_providers.value.filterNot { it.id == id } + saved)
        return saved
    }

    fun delete(id: String) {
        secretStore.remove(id)
        persist(_providers.value.filterNot { it.id == id })
    }

    fun modelEntries(): List<ModelEntry> =
        _providers.value.map { it.toModelEntry(secretStore.get(it.id)) }

    private fun load(): List<ManualProvider> {
        val raw = prefs.getString(KEY_PROVIDERS, null)?.takeIf { it.isNotBlank() } ?: return emptyList()
        val decoded = runCatching { json.decodeFromString<List<ManualProvider>>(raw) }
            .onFailure { Log.w(TAG, "manual provider decode failed: ${it.message}") }
            .getOrDefault(emptyList())
        return decoded.map { provider ->
            provider.copy(
                roles = provider.roles.filter { it in KNOWN_ROLES }
                    .ifEmpty { listOf(Role.TEXT) },
                promptOverrides = provider.promptOverrides.normalized(),
                hasKey = !secretStore.get(provider.id).isNullOrBlank(),
            )
        }
    }

    private fun persist(providers: List<ManualProvider>) {
        _providers.value = providers
        prefs.edit().putString(KEY_PROVIDERS, json.encodeToString(providers)).apply()
    }

    private companion object {
        const val TAG = "PikuDiag"
        const val KEY_PROVIDERS = "llm_manual_providers_v1"
        val KNOWN_ROLES = setOf(Role.TEXT, Role.NOVEL)

        fun manualId(): String = "manual:" + UUID.randomUUID()
    }
}

internal fun normalizeManualBaseUrl(raw: String): String {
    var value = raw.trim()
    if (value.isEmpty()) return value
    value = value.replace(Regex("/+$"), "")
    value = value.replace(Regex("/chat/completions$", RegexOption.IGNORE_CASE), "")
    val uri = runCatching { URI(value) }.getOrNull()
    if (uri != null && (uri.path.isNullOrEmpty() || uri.path == "/")) {
        value = value.trimEnd('/') + "/v1"
    }
    return value
}
