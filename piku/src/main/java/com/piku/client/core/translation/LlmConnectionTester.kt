package com.piku.client.core.translation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class LlmConnectionTester @Inject constructor(
    private val api: LlmChatApi,
) {

    suspend fun test(provider: ManualProvider, apiKey: String?): Result<Long> {
        val key = apiKey.orEmpty()
        if (provider.authMode != AuthMode.NONE && key.isBlank()) {
            return Result.failure(IllegalStateException(MISSING_KEY))
        }
        val imageOnly = Role.IMAGE in provider.roles &&
            Role.TEXT !in provider.roles &&
            Role.NOVEL !in provider.roles
        val body = if (imageOnly) imageProbeBody(provider.model) else textProbeBody(provider.model)
        val started = System.currentTimeMillis()
        return try {
            withTimeout(if (imageOnly) IMAGE_TEST_TIMEOUT_MS else TEST_TIMEOUT_MS) {
                api.chat(
                    url = LlmTranslateEngine.chatUrl(normalizeManualBaseUrl(provider.baseUrl)),
                    headers = authHeaders(provider, key),
                    body = body,
                )
            }
            Result.success(max(1L, System.currentTimeMillis() - started))
        } catch (e: TimeoutCancellationException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun textProbeBody(model: String) = buildJsonObject {
        put("model", JsonPrimitive(model))
        put(
            "messages",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive("ping"))
                    },
                )
            },
        )
        put("stream", JsonPrimitive(false))
    }

    private fun imageProbeBody(model: String) = buildJsonObject {
        put("model", JsonPrimitive(model))
        put(
            "messages",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put(
                            "content",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("type", JsonPrimitive("text"))
                                        put("text", JsonPrimitive("reply with the word ok"))
                                    },
                                )
                                add(
                                    buildJsonObject {
                                        put("type", JsonPrimitive("image_url"))
                                        put(
                                            "image_url",
                                            buildJsonObject { put("url", JsonPrimitive(PROBE_IMAGE)) },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
        put("stream", JsonPrimitive(false))
    }

    suspend fun listModels(provider: ManualProvider, apiKey: String?): Result<List<String>> {
        val key = apiKey.orEmpty()
        if (provider.authMode != AuthMode.NONE && key.isBlank()) {
            return Result.failure(IllegalStateException(MISSING_KEY))
        }
        val baseUrl = normalizeManualBaseUrl(provider.baseUrl)
        if (baseUrl.isBlank()) {
            return Result.failure(IllegalStateException("base url blank"))
        }
        return try {
            val response = withTimeout(TEST_TIMEOUT_MS) {
                api.models(
                    url = modelsUrl(baseUrl),
                    headers = authHeaders(provider, key),
                )
            }
            val models = response.data
                .map { it.id.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
            if (models.isEmpty()) {
                Result.failure(IllegalStateException("empty model list"))
            } else {
                Result.success(models)
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun authHeaders(provider: ManualProvider, key: String): Map<String, String> =
        llmAuthHeaders(
            authMode = provider.authMode,
            authHeader = provider.authHeader,
            apiKey = key,
            extraHeaders = provider.extraHeaders,
        )

    private fun modelsUrl(baseUrl: String): String =
        baseUrl.trimEnd('/') + "/models"

    companion object {
        const val MISSING_KEY = "missing api key"
        private const val TEST_TIMEOUT_MS = 20_000L

        private const val IMAGE_TEST_TIMEOUT_MS = 60_000L

        private const val PROBE_IMAGE =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk" +
                "YPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
    }
}
