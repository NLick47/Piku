package com.piku.client.core.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualProviderPresetsTest {

    @Test
    fun deepseekPresetFillsSkeletonWithoutKey() {
        val draft = ManualProviderPreset.DEEPSEEK.toDraft()

        assertEquals("DeepSeek", draft.name)
        assertEquals("https://api.deepseek.com/v1", draft.baseUrl)
        assertEquals("deepseek-chat", draft.model)
        assertEquals(AuthMode.BEARER, draft.authMode)
        assertTrue(draft.roles.containsAll(listOf(Role.TEXT, Role.NOVEL)))
        assertFalse(draft.hasKey)
    }

    @Test
    fun siliconflowPresetLeavesModelForFetching() {
        val preset = ManualProviderPreset.SILICON_FLOW
        assertEquals("https://api.siliconflow.cn/v1", preset.baseUrl)
        assertEquals("", preset.defaultModel)
        assertTrue(preset.needsKey)
    }

    @Test
    fun ollamaPresetIsNoAuthWithLanPlaceholder() {
        val preset = ManualProviderPreset.OLLAMA
        assertEquals(AuthMode.NONE, preset.authMode)
        assertFalse(preset.needsKey)
        assertEquals("", preset.baseUrl)
        assertTrue(preset.baseUrlPlaceholder.contains("11434"))
    }

    @Test
    fun quickAddListHasDistinctIdsIncludingDeepseekAndSiliconflow() {
        val ids = ManualProviderPreset.QUICK_ADD.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.contains("deepseek"))
        assertTrue(ids.contains("siliconflow"))
    }
}
