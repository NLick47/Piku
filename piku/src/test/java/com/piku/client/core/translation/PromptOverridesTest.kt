package com.piku.client.core.translation

import com.piku.client.core.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptOverridesTest {

    @Test
    fun customSinglePromptDerivesBatchPromptWithMarkers() {
        val overrides = PromptOverrides(single = mapOf("zh" to "只输出译文，人名保留原文。"))

        val prompts = overrides.toPromptSet()
        assertEquals("只输出译文，人名保留原文。", prompts?.single?.get("zh"))
        assertTrue(prompts?.batch?.get("zh").orEmpty().startsWith("只输出译文，人名保留原文。"))
        assertTrue(prompts?.batch?.get("zh").orEmpty().contains("[[n]]"))
        assertNull(prompts?.novel?.get("zh"))
    }

    @Test
    fun settingValueEqualToDefaultRemovesOverride() {
        val default = "目录默认提示词"
        val overrides = PromptOverrides()
            .withValue(Role.TEXT, "zh", "临时覆盖", default)
            .withValue(Role.TEXT, "zh", default, default)

        assertFalse(overrides.hasAny("zh"))
        assertNull(overrides.toPromptSet())
    }

    @Test
    fun blankValueRemovesOverride() {
        val overrides = PromptOverrides(single = mapOf("zh" to "自定义"))
            .withValue(Role.TEXT, "zh", "   ", "默认")

        assertFalse(overrides.hasAny("zh"))
    }

    @Test
    fun unknownLanguageAndBlankValuesAreFiltered() {
        val overrides = PromptOverrides(
            single = mapOf("zh" to "  中文规则  ", "en" to "", "fr" to "unknown"),
            novel = mapOf("ja" to " 小説ルール "),
        ).normalized()

        assertEquals(mapOf("zh" to "中文规则"), overrides.single)
        assertEquals(mapOf("ja" to "小説ルール"), overrides.novel)
    }

    @Test
    fun overridesAreIsolatedPerLanguage() {
        val overrides = PromptOverrides()
            .withValue(Role.TEXT, "zh", "中文规则", "默认")
            .withValue(Role.TEXT, "en", "English rule", "default")

        assertEquals("中文规则", overrides.value(Role.TEXT, "zh"))
        assertEquals("English rule", overrides.value(Role.TEXT, "en"))
        assertTrue(overrides.hasAny("zh"))
        assertFalse(overrides.hasAny("ja"))
    }

    @Test
    fun languageKeyFallsBackToChineseForSystem() {
        assertEquals("zh", PromptOverrides.langKey(AppLanguage.SYSTEM))
        assertEquals("en", PromptOverrides.langKey(AppLanguage.EN))
        assertEquals("ja", PromptOverrides.langKey(AppLanguage.JA))
    }
}
