package com.piku.client.core.translation

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmConnectionTesterTest {

    private class CapturingApi : LlmChatApi {
        var url: String? = null
        var headers: Map<String, String> = emptyMap()
        var modelsUrl: String? = null
        var modelsHeaders: Map<String, String> = emptyMap()
        var lastBody: JsonObject = JsonObject(emptyMap())

        override suspend fun chat(
            url: String,
            headers: Map<String, String>,
            body: JsonObject,
        ): ChatResponse {
            this.url = url
            this.headers = headers
            this.lastBody = body
            return ChatResponse()
        }

        override suspend fun models(
            url: String,
            headers: Map<String, String>,
        ): LlmModelsResponse {
            modelsUrl = url
            modelsHeaders = headers
            return LlmModelsResponse(
                data = listOf(
                    LlmModelItem("zeta"),
                    LlmModelItem("alpha"),
                    LlmModelItem("alpha"),
                    LlmModelItem(" "),
                ),
            )
        }
    }

    private fun provider(authMode: AuthMode = AuthMode.NONE) = ManualProvider(
        baseUrl = "http://192.168.1.10:8000",
        model = "hunyuan-mt",
        authMode = authMode,
    )

    @Test
    fun draftTestNormalizesBareHostBeforeChat() = runTest {
        val api = CapturingApi()
        val result = LlmConnectionTester(api).test(provider(), apiKey = null)

        assertTrue(result.isSuccess)
        assertEquals("http://192.168.1.10:8000/v1/chat/completions", api.url)
        assertTrue(api.headers.isEmpty())
    }

    @Test
    fun bearerTestSendsAuthorization() = runTest {
        val api = CapturingApi()
        LlmConnectionTester(api).test(provider(AuthMode.BEARER), apiKey = "sk-test")

        assertEquals("Bearer sk-test", api.headers["Authorization"])
    }

    @Test
    fun missingKeyFailsBeforeRequest() = runTest {
        val api = CapturingApi()
        val result = LlmConnectionTester(api).test(provider(AuthMode.BEARER), apiKey = null)

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
        assertEquals(LlmConnectionTester.MISSING_KEY, result.exceptionOrNull()?.message)
        assertFalse(api.url != null)
    }

    @Test
    fun listModelsNormalizesUrlDedupsAndSorts() = runTest {
        val api = CapturingApi()
        val result = LlmConnectionTester(api).listModels(
            provider(AuthMode.BEARER),
            apiKey = "sk-test",
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("alpha", "zeta"), result.getOrThrow())
        assertEquals("http://192.168.1.10:8000/v1/models", api.modelsUrl)
        assertEquals("Bearer sk-test", api.modelsHeaders["Authorization"])
    }

    @Test
    fun listModelsFailsBeforeRequestWhenKeyMissing() = runTest {
        val api = CapturingApi()
        val result = LlmConnectionTester(api).listModels(
            provider(AuthMode.BEARER),
            apiKey = null,
        )

        assertTrue(result.isFailure)
        assertNull(api.modelsUrl)
    }

    @Test
    fun imageOnlyProviderProbesWithAnImage() = runTest {
        val api = CapturingApi()
        val imageProvider = provider().copy(roles = listOf(Role.IMAGE))

        LlmConnectionTester(api).test(imageProvider, apiKey = null)

        val body = api.lastBody.toString()
        assertTrue("图像用途应发图片探针，实际 $body", body.contains("image_url"))
        assertTrue(body.contains("data:image/png;base64,"))
    }

    @Test
    fun textProviderKeepsPlainPing() = runTest {
        val api = CapturingApi()

        LlmConnectionTester(api).test(provider(), apiKey = null)

        val body = api.lastBody.toString()
        assertFalse("文本用途不该带图片", body.contains("image_url"))
        assertTrue(body.contains("ping"))
    }
}
