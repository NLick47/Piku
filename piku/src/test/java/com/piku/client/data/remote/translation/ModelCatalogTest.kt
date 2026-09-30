package com.piku.client.data.remote.translation

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    @Test
    fun `default list starts with verified free qwen`() {
        val first = ModelCatalog.default
        assertTrue(first.verified)
        assertTrue(first.free)
        assertEquals("Qwen/Qwen3-8B", first.model)
    }

    @Test
    fun `builtin fallback entries carry no api key`() {
        // 内置列表只是离线兜底，key 只经远程加密目录分发
        assertTrue(ModelCatalog.DEFAULTS.all { it.apiKey.isNullOrBlank() })
    }

    @Test
    fun `builtin entries separate text and novel channels`() {
        // 小文本与小说正文是两条独立通道：内置条目各司其职，无跨场景兼任
        val byId = ModelCatalog.DEFAULTS.associateBy { it.id }
        assertTrue(Role.TEXT in byId.getValue("siliconflow-qwen3-8b").roles)
        assertTrue(Role.TEXT in byId.getValue(ModelCatalog.GLM_ID).roles)
        assertTrue(Role.NOVEL in byId.getValue("scnet-deepseek-novel").roles)
        assertTrue(ModelCatalog.DEFAULTS.none { it.roles.size > 1 })
    }

    @Test
    fun `entries without roles default to text`() {
        // fork 省略 roles 字段时视为文本类，文本选择器仍可见
        val entry = ModelEntry(id = "x", label = "x", baseUrl = "u", model = "m")
        assertEquals(listOf(Role.TEXT), entry.roles)
    }

    @Test
    fun `entry backups keep primary first and declared order`() {
        val entry = ModelEntry(
            id = "x",
            label = "x",
            baseUrl = "https://primary/v1",
            model = "m",
            baseUrls = listOf("https://backup1/v1", "https://backup2/v1"),
        )
        assertEquals(
            listOf("https://primary/v1", "https://backup1/v1", "https://backup2/v1"),
            ModelCatalog.baseUrlCandidates(entry.baseUrl, entry.baseUrls),
        )
    }

    @Test
    fun `pipe separated addresses split and duplicates collapse`() {
        assertEquals(
            listOf("https://a/v1", "https://b/v1"),
            ModelCatalog.baseUrlCandidates("https://a/v1|https://b/v1", listOf("https://a/v1", "  ")),
        )
    }

    @Test
    fun `catalog source id decodes and stays optional`() {
        val json = Json { ignoreUnknownKeys = true }

        val withId = json.decodeFromString<ModelCatalogDto>(
            """{"version":8,"sourceId":"mom09-fork","models":[]}""",
        )
        assertEquals("mom09-fork", withId.sourceId)

        // 没声明 sourceId 的目录（老目录、第三方临时托管）照常解出来，不能因此不可用
        val withoutId = json.decodeFromString<ModelCatalogDto>("""{"version":8,"models":[]}""")
        assertNull(withoutId.sourceId)
    }

    @Test
    fun `legacy and multi entry json both decode with a stable candidate list`() {
        val json = Json { ignoreUnknownKeys = true }

        // 老目录（没有 baseUrls 字段）解码后行为不变
        val legacy = json.decodeFromString<ModelEntry>(
            """{"id":"x","label":"x","baseUrl":"https://only/v1","model":"m"}""",
        )
        assertTrue(legacy.baseUrls.isEmpty())
        assertEquals(listOf("https://only/v1"), ModelCatalog.baseUrlCandidates(legacy.baseUrl, legacy.baseUrls))

        // 新目录的多入口字段能解出来
        val withBackups = json.decodeFromString<ModelEntry>(
            """{"id":"x","label":"x","baseUrl":"https://only/v1","model":"m","baseUrls":["https://b/v1"]}""",
        )
        assertEquals(listOf("https://b/v1"), withBackups.baseUrls)
        assertEquals(
            listOf("https://only/v1", "https://b/v1"),
            ModelCatalog.baseUrlCandidates(withBackups.baseUrl, withBackups.baseUrls),
        )
    }
}
