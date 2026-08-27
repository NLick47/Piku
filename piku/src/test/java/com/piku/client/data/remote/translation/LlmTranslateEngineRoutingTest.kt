package com.piku.client.data.remote.translation

import com.piku.client.domain.translation.ChunkContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 捕获引擎实际发出的请求，验证提示词路由不窜。全部使用合成条目，
 * 离线、确定性、对远程目录形态零假设：
 *
 * - 文本通道（role=text）的批量、单条（size==1）、超长简介三条路径
 *   分别命中 batch/single/single 组；即使该模型的提示词集里带了一个
 *   "有毒的" novel 组，文本通道也绝不可能用到它；
 * - 小说通道（role=novel）带 context 时 novel 组 + 对照表拼尾 + 结构化前缀；
 *   不带 context（首块）仍是 novel 组；
 * - 模型未自带提示词时正确继承目录默认组（覆盖优先级：模型级 > 目录级）；
 * - 目录 params 可覆盖基础参数但不可触碰 model/messages/stream/n 保留字段；
 * - 缓存键 engineId 按 role/model/baseUrl 三元组隔离。
 */
class LlmTranslateEngineRoutingTest {

    private class CapturingApi : LlmChatApi {
        data class Call(val url: String, val authorization: String, val body: JsonObject)

        val calls = mutableListOf<Call>()
        var responder: (JsonObject) -> String = { "这是合格的中文译文" }

        override suspend fun chat(url: String, authorization: String, body: JsonObject): ChatResponse {
            calls += Call(url, authorization, body)
            return ChatResponse(
                choices = listOf(ChatChoice(message = ChatMessage(role = "assistant", content = responder(body)))),
            )
        }
    }

    private companion object {
        const val TARGET_ZH = "Simplified Chinese"

        const val DEFAULT_SINGLE = "目录默认·单条规则：只输出译文本体"
        const val DEFAULT_BATCH = "目录默认·批量规则：按 [[1]] 双方括号标记逐条回复"
        const val DEFAULT_NOVEL = "目录默认·小说规则：⟦上文原句⟧仅供衔接理解，只翻译 ⟦待翻正文⟧ 之后的部分"

        /** 模型自带的精简覆盖版（模拟真实目录里文本小模型的形态） */
        const val MODEL_SINGLE = "模型级·单条精简规则"
        const val MODEL_BATCH = "模型级·批量精简规则 [[1]]"

        /**
         * 有毒哨兵：若文本通道的任何路径捕获到这个字符串，说明 novel 组窜入。
         * 放进文本模型的提示词集里，让"窜用"在等值断言下必然暴露。
         */
        const val POISONED_NOVEL = "模型级·小说组（文本通道绝不允许出现）"

        val contextMarkers = listOf("⟦上文原句⟧", "⟦上文译文⟧", "⟦待翻正文⟧")
    }

    private val defaults = CatalogDefaults(
        params = mapOf("temperature" to JsonPrimitive(0.2)),
        prompts = PromptSet(
            single = mapOf("zh" to DEFAULT_SINGLE),
            batch = mapOf("zh" to DEFAULT_BATCH),
            novel = mapOf("zh" to DEFAULT_NOVEL),
        ),
    )

    private val textEntryWithPrompts = ModelEntry(
        id = "text-with-prompts",
        label = "text",
        baseUrl = "https://text.example.com/v1",
        model = "text-model-a",
        apiKey = "k",
        roles = listOf(Role.TEXT),
        prompts = PromptSet(
            single = mapOf("zh" to MODEL_SINGLE),
            batch = mapOf("zh" to MODEL_BATCH),
            novel = mapOf("zh" to POISONED_NOVEL),
        ),
    )

    private val novelEntry = ModelEntry(
        id = "novel-inherits",
        label = "novel",
        baseUrl = "https://novel.example.com/v1",
        model = "novel-model-b",
        apiKey = "k",
        roles = listOf(Role.NOVEL),
    )

    private fun engine(api: CapturingApi, role: String, entry: ModelEntry?): LlmTranslateEngine =
        LlmTranslateEngine(
            api,
            TranslationEngineFactory.buildConfig(
                apiKey = "test-key",
                role = role,
                catalogEntry = entry,
                defaults = defaults,
                fallbackBaseUrl = "https://fallback.example.com/v1",
                fallbackModel = "fallback-model",
            ),
        )

    private fun JsonObject.systemMessage(): String =
        this["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content

    private fun JsonObject.userMessage(): String =
        this["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content

    private fun CapturingApi.batchResponder() {
        responder = { body ->
            val user = body.userMessage()
            val n = Regex("\\[\\[(\\d+)]]").findAll(user).toList().size
            if (n > 1) {
                (1..n).joinToString("\n") { "[[$it]]\n第${it}条合格译文" }
            } else {
                "这是合格的中文译文"
            }
        }
    }

    private fun assertNoNovelLeak(system: String) {
        assertFalse("novel 组窜入文本通道", POISONED_NOVEL == system)
        contextMarkers.forEach { marker ->
            assertFalse("文本通道窜入小说上下文标记 $marker", marker in system)
        }
    }

    @Test
    fun `text channel batches short texts under batch prompt`() = runTest {
        val api = CapturingApi().apply { batchResponder() }
        engine(api, Role.TEXT, textEntryWithPrompts).translate(
            listOf("これは一行目です", "これは二行目です"),
            TARGET_ZH,
        )
        assertEquals(1, api.calls.size)
        val call = api.calls.single()
        assertEquals(LlmTranslateEngine.chatUrl(textEntryWithPrompts.baseUrl), call.url)
        assertEquals("Bearer test-key", call.authorization)
        assertEquals(MODEL_BATCH, call.body.systemMessage())
        assertTrue(call.body.userMessage().startsWith("[[1]]"))
        assertEquals(textEntryWithPrompts.model, call.body["model"]!!.jsonPrimitive.content)
        assertNoNovelLeak(call.body.systemMessage())
    }

    @Test
    fun `text channel single short item uses single prompt not novel`() = runTest {
        // 回归：批量仅剩 1 条时曾经 translateSingle 固定拿到小说组
        val api = CapturingApi()
        engine(api, Role.TEXT, textEntryWithPrompts).translate(listOf("これはテストです"), TARGET_ZH)
        val system = api.calls.single().body.systemMessage()
        assertEquals(MODEL_SINGLE, system)
        assertNoNovelLeak(system)
    }

    @Test
    fun `text channel long description uses single prompt not novel`() = runTest {
        // 回归：短字段里超过 LONG_TEXT_THRESHOLD 的长简介曾被分进小说路径
        val longDescription = buildString { repeat(50) { append("これは非常に長い紹介文です。") } }
        assertTrue(longDescription.length > LlmTranslateEngine.LONG_TEXT_THRESHOLD)
        val api = CapturingApi()
        engine(api, Role.TEXT, textEntryWithPrompts).translate(listOf(longDescription), TARGET_ZH)
        val system = api.calls.single().body.systemMessage()
        assertEquals(MODEL_SINGLE, system)
        assertNoNovelLeak(system)
    }

    @Test
    fun `text model without own prompts inherits catalog defaults per group`() = runTest {
        // 覆盖优先级：模型缺省的组逐级落目录默认，而不是跳到别的组
        val inheriting = textEntryWithPrompts.copy(prompts = null)
        val api = CapturingApi()
        engine(api, Role.TEXT, inheriting).translate(listOf("これはテストです"), TARGET_ZH)
        val system = api.calls.single().body.systemMessage()
        assertEquals(DEFAULT_SINGLE, system)
        assertNoNovelLeak(system)
    }

    @Test
    fun `novel chunk with context appends glossary to novel prompt`() = runTest {
        val glossary = "【本作标签对照 · 人名等专名严格照此翻译】\nタグ→标签"
        val context = ChunkContext(
            originalTail = "前の文章の末尾。",
            translatedTail = "前一句译文的末尾。",
            glossaryBlock = glossary,
        )
        val api = CapturingApi()
        engine(api, Role.NOVEL, novelEntry).translate(listOf("これは本文です"), TARGET_ZH, context)
        assertEquals(1, api.calls.size)
        val call = api.calls.single()
        assertEquals("$DEFAULT_NOVEL\n\n$glossary", call.body.systemMessage())
        assertEquals(
            "⟦上文原句⟧\n前の文章の末尾。\n⟦上文译文⟧\n前一句译文的末尾。\n⟦待翻正文⟧\nこれは本文です",
            call.body.userMessage(),
        )
        assertEquals(novelEntry.model, call.body["model"]!!.jsonPrimitive.content)
        assertEquals(LlmTranslateEngine.chatUrl(novelEntry.baseUrl), call.url)
    }

    @Test
    fun `novel first chunk without context still gets novel prompt`() = runTest {
        // 回归：首块无 context 且 ≤600 字时会经 translateBatch(size==1)。
        // 精确等于期望的 novel 组提示词（含结构标记），single/batch 组不可能撞上
        val api = CapturingApi()
        engine(api, Role.NOVEL, novelEntry).translate(listOf("これはテストです"), TARGET_ZH)
        assertEquals(DEFAULT_NOVEL, api.calls.single().body.systemMessage())
    }

    @Test
    fun `catalog params merge but reserved fields stay intact`() = runTest {
        val entry = textEntryWithPrompts.copy(
            params = mapOf(
                "temperature" to JsonPrimitive(0.9),
                "stream" to JsonPrimitive(true),
                "top_p" to JsonPrimitive(0.5),
            ),
        )
        val api = CapturingApi()
        engine(api, Role.TEXT, entry).translate(listOf("これはテストです"), TARGET_ZH)
        val body = api.calls.single().body
        // 单模型覆盖基础与目录默认
        assertEquals("0.9", body["temperature"]!!.jsonPrimitive.content)
        assertEquals("0.5", body["top_p"]!!.jsonPrimitive.content)
        // stream 属结构性保留字段，目录再怎么下发也必须非流式
        assertEquals("false", body["stream"]!!.jsonPrimitive.content)
    }

    @Test
    fun `engineId separates role model and base url`() {
        val textEngine = engine(CapturingApi(), Role.TEXT, textEntryWithPrompts)
        val novelEngine = engine(CapturingApi(), Role.NOVEL, textEntryWithPrompts)
        assertNotEquals("同一条目不同场景必须隔离缓存命名空间", textEngine.engineId, novelEngine.engineId)
        assertTrue(":${Role.TEXT}:" in textEngine.engineId)
        assertTrue(":${Role.NOVEL}:" in novelEngine.engineId)
        assertTrue(textEntryWithPrompts.model in textEngine.engineId)
        // baseUrl 尾斜杠不影响同一命名空间
        assertEquals(
            textEngine.engineId,
            engine(CapturingApi(), Role.TEXT, textEntryWithPrompts.copy(baseUrl = textEntryWithPrompts.baseUrl + "/")).engineId,
        )
    }

    @Test
    fun `custom model outside catalog falls back to settings values`() {
        val cfg = TranslationEngineFactory.buildConfig(
            apiKey = "k",
            role = Role.TEXT,
            catalogEntry = null,
            defaults = null,
            fallbackBaseUrl = "https://fallback.example.com/v1",
            fallbackModel = "fallback-model",
        )
        assertEquals("https://fallback.example.com/v1", cfg.baseUrl)
        assertEquals("fallback-model", cfg.model)
        assertEquals(Role.TEXT, cfg.role)
    }
}
