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
}
