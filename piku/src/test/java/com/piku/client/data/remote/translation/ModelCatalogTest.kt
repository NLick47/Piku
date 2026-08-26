package com.piku.client.data.remote.translation

import org.junit.Assert.assertEquals
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
}
