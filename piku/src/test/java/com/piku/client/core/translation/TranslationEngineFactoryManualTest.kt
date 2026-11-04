package com.piku.client.core.translation

import com.piku.client.core.storage.prefs.InMemorySharedPreferences
import com.piku.client.core.storage.prefs.SettingsRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationEngineFactoryManualTest {

    private object NoopApi : LlmChatApi {
        override suspend fun chat(
            url: String,
            headers: Map<String, String>,
            body: JsonObject,
        ): ChatResponse = error("配置测试不应发出请求")

        override suspend fun models(
            url: String,
            headers: Map<String, String>,
        ): LlmModelsResponse = error("配置测试不应发出请求")
    }

    private val manual = ModelEntry(
        id = "manual:1",
        label = "本地混元",
        baseUrl = "http://192.168.1.10:8000/v1",
        model = "hunyuan-mt",
        apiKey = null,
        roles = listOf(Role.TEXT),
        authMode = AuthMode.NONE,
        isManual = true,
        params = mapOf("custom" to JsonPrimitive(7)),
    )

    private val catalogDefaults = CatalogDefaults(
        params = mapOf("enable_thinking" to JsonPrimitive(false)),
        prompts = PromptSet(single = mapOf("zh" to "目录共享提示词")),
    )

    @Test
    fun manualProviderInheritsCatalogPromptsButNotParams() {
        val cfg = TranslationEngineFactory.buildConfig(
            apiKey = "",
            role = Role.TEXT,
            catalogEntry = manual,
            defaults = catalogDefaults,
            fallbackBaseUrl = "https://unused/v1",
            fallbackModel = "unused",
        )

        assertFalse(cfg.params.containsKey("enable_thinking"))
        assertEquals(7, cfg.params["custom"]?.jsonPrimitive?.int)
        assertEquals(catalogDefaults.prompts, cfg.defaultPrompts)
        assertNull(cfg.prompts)
        assertEquals(AuthMode.NONE, cfg.authMode)
        assertEquals("hunyuan-mt", cfg.model)
    }

    @Test
    fun manualProviderPromptOverridesWinOverCatalogDefaults() {
        val provider = ManualProvider(
            id = "manual:2",
            baseUrl = "http://192.168.1.10:8000/v1",
            model = "hunyuan-mt",
            authMode = AuthMode.NONE,
            promptOverrides = PromptOverrides(single = mapOf("zh" to "Provider 自定义规则")),
        )
        val cfg = TranslationEngineFactory.buildConfig(
            apiKey = "",
            role = Role.TEXT,
            catalogEntry = provider.toModelEntry(null),
            defaults = catalogDefaults,
            fallbackBaseUrl = "https://unused/v1",
            fallbackModel = "unused",
        )

        assertEquals("Provider 自定义规则", cfg.prompts?.single?.get("zh"))
        assertTrue(cfg.prompts?.batch?.get("zh").orEmpty().contains("[[n]]"))
        assertEquals(catalogDefaults.prompts, cfg.defaultPrompts)
    }

    @Test
    fun manualNoAuthProviderCreatableWithBlankKey() {
        val factory = TranslationEngineFactory(
            api = NoopApi,
            settingsRepository = SettingsRepository(InMemorySharedPreferences()),
        )

        assertNotNull(factory.create(apiKey = "", role = Role.TEXT, catalogEntry = manual))
    }
}
