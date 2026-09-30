package com.piku.client.data.remote.translation

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * 同模型多入口（目录 models[].baseUrls / baseUrl 里 "|" 分隔）：主地址失败按序回退，
 * 某个地址成功后记住它、下一条请求直接用它；全部失败才把错误交给上层（重试/换模型）。
 * 引擎建在假的 LlmChatApi 上，断言的就是「实际打了哪些 URL、顺序如何」。
 */
class LlmTranslateEngineAddressTest {

    private class AddressAwareApi(private val healthy: Set<String>) : LlmChatApi {
        val urls = mutableListOf<String>()

        override suspend fun chat(url: String, authorization: String, body: JsonObject): ChatResponse {
            urls += url
            val base = url.removeSuffix("/chat/completions")
            if (base !in healthy) throw IOException("connect failed: $base")
            return ChatResponse(
                choices = listOf(ChatChoice(message = ChatMessage(role = "assistant", content = "初次见面"))),
            )
        }
    }

    private val source = "はじめまして"
    private val primary = "https://primary.example/v1"
    private val backup = "https://backup.example/v1"
    private val backup2 = "https://backup2.example/v1"

    private fun config(baseUrl: String = primary, baseUrls: List<String> = emptyList()) =
        LlmTranslateEngine.LlmConfig(
            baseUrl = baseUrl,
            baseUrls = baseUrls,
            apiKey = "test-key",
            model = "test-model",
            role = Role.TEXT,
        )

    private fun urlsOf(vararg bases: String) = bases.map { "$it/chat/completions" }

    @Before
    fun setUp() = LlmAddressPreference.clear()

    @After
    fun tearDown() = LlmAddressPreference.clear()

    @Test
    fun `primary failure falls back to the backup address`() = runTest {
        val api = AddressAwareApi(healthy = setOf(backup))
        val engine = LlmTranslateEngine(api, config(baseUrls = listOf(backup)))

        assertEquals(listOf("初次见面"), engine.translate(listOf(source), LlmTranslateEngine.TARGET_ZH, null))
        assertEquals(urlsOf(primary, backup), api.urls)
    }

    @Test
    fun `healthy primary never touches the backups`() = runTest {
        val api = AddressAwareApi(healthy = setOf(primary, backup, backup2))
        val engine = LlmTranslateEngine(api, config(baseUrls = listOf(backup, backup2)))

        engine.translate(listOf(source), LlmTranslateEngine.TARGET_ZH, null)
        assertEquals(urlsOf(primary), api.urls)
    }

    @Test
    fun `backups are tried in the declared order`() = runTest {
        val api = AddressAwareApi(healthy = setOf(backup2))
        val engine = LlmTranslateEngine(api, config(baseUrls = listOf(backup, backup2)))

        engine.translate(listOf(source), LlmTranslateEngine.TARGET_ZH, null)
        assertEquals(urlsOf(primary, backup, backup2), api.urls)
    }

    @Test
    fun `working backup is remembered for the next request`() = runTest {
        val api = AddressAwareApi(healthy = setOf(backup))
        LlmTranslateEngine(api, config(baseUrls = listOf(backup)))
            .translate(listOf(source), LlmTranslateEngine.TARGET_ZH, null)
        assertEquals(urlsOf(primary, backup), api.urls)

        // 下一次翻译会新建引擎（工厂每次都重建）：不该再白撞一次死掉的主地址
        api.urls.clear()
        LlmTranslateEngine(api, config(baseUrls = listOf(backup)))
            .translate(listOf(source), LlmTranslateEngine.TARGET_ZH, null)
        assertEquals(urlsOf(backup), api.urls)
    }

    @Test
    fun `pipe separated addresses expand like the array form`() = runTest {
        val api = AddressAwareApi(healthy = setOf(backup))
        val engine = LlmTranslateEngine(api, config(baseUrl = "$primary|$backup"))

        engine.translate(listOf(source), LlmTranslateEngine.TARGET_ZH, null)
        assertEquals(urlsOf(primary, backup), api.urls)
    }

    @Test
    fun `all addresses failing yields empty translation and tries every address`() = runTest {
        val api = AddressAwareApi(healthy = emptySet())
        val engine = LlmTranslateEngine(api, config(baseUrls = listOf(backup)))

        assertEquals(listOf(""), engine.translate(listOf(source), LlmTranslateEngine.TARGET_ZH, null))
        // 每一次尝试都走满整条候选链（主 → 备），一条都不漏
        assertTrue(api.urls.size >= 2)
        assertEquals(urlsOf(primary, backup), api.urls.take(2))
    }

    @Test
    fun `factory config carries entry backups and settings typed pipes`() {
        val entry = ModelEntry(
            id = "x",
            label = "x",
            baseUrl = primary,
            model = "m",
            baseUrls = listOf(backup, backup2),
        )
        val fromCatalog = TranslationEngineFactory.buildConfig(
            apiKey = "k",
            role = Role.TEXT,
            catalogEntry = entry,
            defaults = null,
            fallbackBaseUrl = "https://unused/v1",
            fallbackModel = "fm",
        )
        assertEquals(listOf(primary, backup, backup2), fromCatalog.candidates())

        // 列表外的自定义模型：设置里手填的地址同样支持 "|" 分隔
        val fromSettings = TranslationEngineFactory.buildConfig(
            apiKey = "k",
            role = Role.TEXT,
            catalogEntry = null,
            defaults = null,
            fallbackBaseUrl = "$primary|$backup",
            fallbackModel = "fm",
        )
        assertEquals(listOf(primary, backup), fromSettings.candidates())
    }
}
