package com.piku.client.core.translation

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ThinkingParamRequestTest {

    private class CapturingApi : LlmChatApi {
        var lastBody: JsonObject? = null

        override suspend fun chat(
            url: String,
            headers: Map<String, String>,
            body: JsonObject,
        ): ChatResponse {
            lastBody = body
            return ChatResponse(
                choices = listOf(ChatChoice(message = ChatMessage(role = "assistant", content = "早上好"))),
            )
        }

        override suspend fun models(url: String, headers: Map<String, String>): LlmModelsResponse =
            LlmModelsResponse()
    }

    @Before
    fun setUp() = LlmAddressPreference.clear()

    @After
    fun tearDown() = LlmAddressPreference.clear()

    private fun manualEntry(baseUrl: String, model: String, params: Map<String, kotlinx.serialization.json.JsonElement>) =
        ModelEntry(
            id = "manual:1",
            label = "手动",
            baseUrl = baseUrl,
            model = model,
            apiKey = null,
            roles = listOf(Role.TEXT),
            authMode = AuthMode.NONE,
            isManual = true,
            params = params,
        )

    private suspend fun requestBodyFor(
        baseUrl: String,
        model: String,
        thinkingEnabled: Boolean,
        mode: ThinkingParam? = null,
    ): JsonObject {
        val params = applyThinking(
            params = emptyMap(),
            thinkingEnabled = thinkingEnabled,
            mode = mode ?: inferThinkingParam(baseUrl, model),
            touched = true,
        )
        val cfg = TranslationEngineFactory.buildConfig(
            apiKey = "",
            role = Role.TEXT,
            catalogEntry = manualEntry(baseUrl, model, params),
            defaults = null,
            fallbackBaseUrl = baseUrl,
            fallbackModel = model,
        )
        val api = CapturingApi()
        LlmTranslateEngine(api, cfg).translate(listOf("おはよう"), LlmTranslateEngine.TARGET_ZH, null)
        return api.lastBody ?: error("no request captured")
    }

    @Test
    fun qwenThinkingOffReachesRequestBody() = runTest {
        val body = requestBodyFor("https://api.siliconflow.cn/v1", "Qwen/Qwen3-8B", thinkingEnabled = false)

        assertFalse(body.getValue("enable_thinking").jsonPrimitive.content.toBoolean())
    }

    @Test
    fun glmThinkingOffReachesRequestBodyAsObject() = runTest {
        val body = requestBodyFor("https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", thinkingEnabled = false)

        assertEquals(
            "disabled",
            body.getValue("thinking").jsonObject.getValue("type").jsonPrimitive.content,
        )
        assertFalse(body.containsKey("enable_thinking"))
    }

    @Test
    fun vllmThinkingOffNestsInRequestBody() = runTest {
        val body = requestBodyFor("http://192.168.1.10:8000/v1", "Qwen/Qwen3-8B", thinkingEnabled = false)

        assertFalse(body.containsKey("enable_thinking"))
        val nested = body.getValue("chat_template_kwargs").jsonObject
        assertFalse(nested.getValue("enable_thinking").jsonPrimitive.content.toBoolean())
    }

    @Test
    fun explicitShapeOverridesInference() = runTest {
        val body = requestBodyFor(
            "http://192.168.1.10:8000/v1",
            "Qwen/Qwen3-8B",
            thinkingEnabled = false,
            mode = ThinkingParam.ENABLE_THINKING,
        )

        assertFalse(body.containsKey("chat_template_kwargs"))
        assertFalse(body.getValue("enable_thinking").jsonPrimitive.content.toBoolean())
    }

    @Test
    fun thinkingOnSendsNoDisableFlag() = runTest {
        val qwen = requestBodyFor("https://api.siliconflow.cn/v1", "Qwen/Qwen3-8B", thinkingEnabled = true)
        assertFalse(qwen.containsKey("enable_thinking"))

        val glm = requestBodyFor("https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", thinkingEnabled = true)
        assertFalse(glm.containsKey("thinking"))
    }

    @Test
    fun reservedKeysCannotBeOverridden() = runTest {
        val params = mapOf("stream" to kotlinx.serialization.json.JsonPrimitive(true))
        val cfg = TranslationEngineFactory.buildConfig(
            apiKey = "",
            role = Role.TEXT,
            catalogEntry = manualEntry("https://api.example.com/v1", "m", params),
            defaults = null,
            fallbackBaseUrl = "https://api.example.com/v1",
            fallbackModel = "m",
        )
        val api = CapturingApi()
        LlmTranslateEngine(api, cfg).translate(listOf("おはよう"), LlmTranslateEngine.TARGET_ZH, null)

        assertFalse(api.lastBody!!.getValue("stream").jsonPrimitive.content.toBoolean())
    }
}
