package com.piku.client.core.translation

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingParamsTest {

    @Test
    fun cloudShapeUsesTopLevelFlag() {
        val params = thinkingOffParams(ThinkingParam.ENABLE_THINKING)

        assertEquals(setOf("enable_thinking"), params.keys)
        assertFalse(params.getValue("enable_thinking").jsonPrimitive.boolean)
    }

    @Test
    fun vllmShapeNestsInsideChatTemplateKwargs() {
        val params = thinkingOffParams(ThinkingParam.CHAT_TEMPLATE_KWARGS)

        assertEquals(setOf("chat_template_kwargs"), params.keys)
        val nested = params.getValue("chat_template_kwargs").jsonObject
        assertFalse(nested.getValue("enable_thinking").jsonPrimitive.boolean)
    }

    @Test
    fun glmShapeUsesTypeDisabled() {
        val params = thinkingOffParams(ThinkingParam.THINKING_OBJECT)

        assertEquals(setOf("thinking"), params.keys)
        assertEquals("disabled", params.getValue("thinking").jsonObject.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun infersShapeFromAddressAndModel() {
        assertEquals(
            ThinkingParam.THINKING_OBJECT,
            inferThinkingParam("https://open.bigmodel.cn/api/paas/v4", "glm-4-flash"),
        )
        assertEquals(
            ThinkingParam.THINKING_OBJECT,
            inferThinkingParam("https://some-gateway.example.com/v1", "glm-4.6"),
        )
        assertEquals(
            ThinkingParam.ENABLE_THINKING,
            inferThinkingParam("https://api.siliconflow.cn/v1", "Qwen/Qwen3-8B"),
        )
    }

    @Test
    fun localAddressesDefaultToNestedShape() {
        listOf(
            "http://192.168.1.10:8000/v1",
            "http://localhost:8000/v1",
            "http://127.0.0.1:8080/v1",
            "http://10.0.0.5:8000/v1",
            "http://172.16.0.9:8000/v1",
            "http://172.31.5.5:8000/v1",
            "http://mymac.local:8000/v1",
        ).forEach { url ->
            assertEquals("$url 应按本地处理", ThinkingParam.CHAT_TEMPLATE_KWARGS, inferThinkingParam(url, "qwen3"))
        }
        assertEquals(
            ThinkingParam.ENABLE_THINKING,
            inferThinkingParam("http://172.32.0.1:8000/v1", "qwen3"),
        )
    }

    @Test
    fun applyThinkingOffWritesDisabledFlag() {
        val params = applyThinking(
            params = emptyMap(),
            thinkingEnabled = false,
            mode = ThinkingParam.ENABLE_THINKING,
            touched = true,
        )

        assertTrue(isThinkingDisabled(params))
    }

    @Test
    fun untouchedWithoutFieldsWritesNothing() {
        val existing = mapOf("top_p" to JsonPrimitive(0.9))

        val params = applyThinking(
            params = existing,
            thinkingEnabled = false,
            mode = ThinkingParam.ENABLE_THINKING,
            touched = false,
        )

        assertEquals(existing, params)
        assertFalse(params.containsKey("enable_thinking"))
    }

    @Test
    fun untouchedWithFieldsStillNormalizes() {
        val existing = mapOf("enable_thinking" to JsonPrimitive(false))

        val params = applyThinking(
            params = existing,
            thinkingEnabled = false,
            mode = ThinkingParam.THINKING_OBJECT,
            touched = false,
        )

        assertEquals(setOf("thinking"), params.keys)
        assertTrue(isThinkingDisabled(params))
    }

    @Test
    fun applyThinkingOnRemovesAllShapes() {
        val existing = mapOf(
            "enable_thinking" to JsonPrimitive(false),
            "chat_template_kwargs" to JsonObject(mapOf("enable_thinking" to JsonPrimitive(false))),
            "thinking" to JsonObject(mapOf("type" to JsonPrimitive("disabled"))),
        )

        val params = applyThinking(
            existing,
            thinkingEnabled = true,
            mode = ThinkingParam.THINKING_OBJECT,
            touched = true,
        )

        assertTrue(params.isEmpty())
    }

    @Test
    fun switchingShapeDropsOtherFields() {
        val cloud = applyThinking(
            emptyMap(),
            thinkingEnabled = false,
            mode = ThinkingParam.ENABLE_THINKING,
            touched = true,
        )
        assertTrue(isThinkingDisabled(cloud))

        val vllm = applyThinking(
            cloud,
            thinkingEnabled = false,
            mode = ThinkingParam.CHAT_TEMPLATE_KWARGS,
            touched = true,
        )
        assertEquals(setOf("chat_template_kwargs"), vllm.keys)
        assertTrue(isThinkingDisabled(vllm))

        val glm = applyThinking(
            vllm,
            thinkingEnabled = false,
            mode = ThinkingParam.THINKING_OBJECT,
            touched = true,
        )
        assertEquals(setOf("thinking"), glm.keys)
        assertTrue(isThinkingDisabled(glm))
    }

    @Test
    fun unrelatedParamsSurvive() {
        val existing = mapOf("top_p" to JsonPrimitive(0.9), "enable_thinking" to JsonPrimitive(false))

        val params = applyThinking(
            existing,
            thinkingEnabled = true,
            mode = ThinkingParam.ENABLE_THINKING,
            touched = true,
        )

        assertEquals(JsonPrimitive(0.9), params["top_p"])
    }

    @Test
    fun readsEnabledAndDisabledFromAllShapes() {
        assertTrue(isThinkingEnabled(mapOf("enable_thinking" to JsonPrimitive(true))))
        assertTrue(
            isThinkingEnabled(
                mapOf("chat_template_kwargs" to JsonObject(mapOf("enable_thinking" to JsonPrimitive(true)))),
            ),
        )
        assertTrue(
            isThinkingEnabled(mapOf("thinking" to JsonObject(mapOf("type" to JsonPrimitive("enabled"))))),
        )

        assertTrue(isThinkingDisabled(mapOf("enable_thinking" to JsonPrimitive(false))))
        assertTrue(
            isThinkingDisabled(
                mapOf("chat_template_kwargs" to JsonObject(mapOf("enable_thinking" to JsonPrimitive(false)))),
            ),
        )
        assertTrue(
            isThinkingDisabled(mapOf("thinking" to JsonObject(mapOf("type" to JsonPrimitive("disabled"))))),
        )

        assertFalse(isThinkingEnabled(emptyMap()))
        assertFalse(isThinkingDisabled(emptyMap()))
    }

    @Test
    fun reverseInfersShapeFromParams() {
        assertNull(thinkingParamOf(emptyMap()))
        assertEquals(
            ThinkingParam.ENABLE_THINKING,
            thinkingParamOf(mapOf("enable_thinking" to JsonPrimitive(false))),
        )
        assertEquals(
            ThinkingParam.CHAT_TEMPLATE_KWARGS,
            thinkingParamOf(
                mapOf("chat_template_kwargs" to JsonObject(mapOf("enable_thinking" to JsonPrimitive(false)))),
            ),
        )
        assertEquals(
            ThinkingParam.THINKING_OBJECT,
            thinkingParamOf(mapOf("thinking" to JsonObject(mapOf("type" to JsonPrimitive("disabled"))))),
        )
    }

    @Test
    fun enumRoundTripsByName() {
        ThinkingParam.entries.forEach { assertEquals(it, ThinkingParam.fromName(it.name)) }
        assertNull(ThinkingParam.fromName("nope"))
        assertNull(ThinkingParam.fromName(null))
    }
}
