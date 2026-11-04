package com.piku.client.core.translation

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LlmTranslateEngineAuthTest {

    private class CapturingApi : LlmChatApi {
        var headers: Map<String, String> = emptyMap()

        override suspend fun chat(
            url: String,
            headers: Map<String, String>,
            body: JsonObject,
        ): ChatResponse {
            this.headers = headers
            return ChatResponse(
                choices = listOf(ChatChoice(message = ChatMessage(role = "assistant", content = "早上好"))),
            )
        }

        override suspend fun models(url: String, headers: Map<String, String>): LlmModelsResponse =
            LlmModelsResponse()
    }

    private fun config(
        authMode: AuthMode,
        apiKey: String,
        authHeader: String = ManualProvider.DEFAULT_AUTH_HEADER,
        extraHeaders: Map<String, String> = emptyMap(),
    ) = LlmTranslateEngine.LlmConfig(
        baseUrl = "http://192.168.1.10:8000/v1",
        apiKey = apiKey,
        model = "hunyuan-mt",
        role = Role.TEXT,
        authMode = authMode,
        authHeader = authHeader,
        extraHeaders = extraHeaders,
    )

    @Before
    fun setUp() = LlmAddressPreference.clear()

    @After
    fun tearDown() = LlmAddressPreference.clear()

    @Test
    fun noAuthLocalServiceWorksWithBlankKeyAndNoAuthHeader() = runTest {
        val api = CapturingApi()
        val result = LlmTranslateEngine(api, config(AuthMode.NONE, apiKey = ""))
            .translate(listOf("おはよう"), LlmTranslateEngine.TARGET_ZH, null)

        assertEquals(listOf("早上好"), result)
        assertTrue(api.headers.isEmpty())
    }

    @Test
    fun bearerAuthSendsAuthorization() = runTest {
        val api = CapturingApi()
        LlmTranslateEngine(api, config(AuthMode.BEARER, apiKey = "sk-bearer"))
            .translate(listOf("おはよう"), LlmTranslateEngine.TARGET_ZH, null)

        assertEquals("Bearer sk-bearer", api.headers["Authorization"])
    }

    @Test
    fun customHeaderAuthUsesConfiguredHeader() = runTest {
        val api = CapturingApi()
        LlmTranslateEngine(
            api,
            config(AuthMode.HEADER, apiKey = "sk-header", authHeader = "api-key"),
        ).translate(listOf("おはよう"), LlmTranslateEngine.TARGET_ZH, null)

        assertEquals("sk-header", api.headers["api-key"])
        assertFalse(api.headers.containsKey("Authorization"))
    }

    @Test
    fun extraHeadersSentAndAuthWinsOnCollision() = runTest {
        val api = CapturingApi()
        LlmTranslateEngine(
            api,
            config(
                AuthMode.BEARER,
                apiKey = "sk-winner",
                extraHeaders = mapOf("X-Trace" to "1", "Authorization" to "Bearer wrong"),
            ),
        ).translate(listOf("おはよう"), LlmTranslateEngine.TARGET_ZH, null)

        assertEquals("1", api.headers["X-Trace"])
        assertEquals("Bearer sk-winner", api.headers["Authorization"])
    }

    @Test
    fun missingBearerKeyYieldsNoRequest() = runTest {
        val api = CapturingApi()
        val result = LlmTranslateEngine(api, config(AuthMode.BEARER, apiKey = ""))
            .translate(listOf("おはよう"), LlmTranslateEngine.TARGET_ZH, null)

        assertEquals(listOf(""), result)
        assertTrue(api.headers.isEmpty())
    }
}
