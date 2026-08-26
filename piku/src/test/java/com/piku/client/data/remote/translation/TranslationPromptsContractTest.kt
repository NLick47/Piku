package com.piku.client.data.remote.translation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置提示词（[TranslationPrompts]，代码层兜底）的"不窜"契约，无条件锁死：
 *
 * - single/batch/novel × zh/en/ja 九槽齐备、同语言三组两两不同；
 * - 上下文结构标记（⟦上文原句⟧/⟦待翻正文⟧）只在 novel 组——
 *   回声剥离按这些标记精确匹配，出现在别的组会诱导模型复读；
 * - [[1]] 双方括号 few-shot 示例只在 batch 组——与 parseBatch 解析契约耦合。
 *
 * 目录下发内容无论如何变化，这层兜底保证最坏情况下提示词也不窜。
 */
class TranslationPromptsContractTest {

    private val contextMarkers = listOf("⟦上文原句⟧", "⟦上文译文⟧", "⟦待翻正文⟧")

    private val languages = listOf("zh", "en", "ja")

    private fun builtin(group: String, lang: String): String {
        val targetLang = when (lang) {
            "en" -> "English"
            "ja" -> "Japanese"
            else -> "Simplified Chinese"
        }
        return when (group) {
            "batch" -> TranslationPrompts.batchSystemPrompt(targetLang)
            "novel" -> TranslationPrompts.novelSystemPrompt(targetLang)
            else -> TranslationPrompts.singleSystemPrompt(targetLang)
        }
    }

    @Test
    fun `builtin prompts are complete across groups and languages`() {
        languages.forEach { lang ->
            listOf("single", "batch", "novel").forEach { group ->
                assertTrue("内置 $group[$lang] 为空", builtin(group, lang).isNotBlank())
            }
        }
    }

    @Test
    fun `builtin groups stay pairwise distinct per language`() {
        languages.forEach { lang ->
            val single = builtin("single", lang)
            val batch = builtin("batch", lang)
            val novel = builtin("novel", lang)
            assertTrue("single 与 batch 相同 [$lang]", single != batch)
            assertTrue("single 与 novel 相同 [$lang]", single != novel)
            assertTrue("batch 与 novel 相同 [$lang]", batch != novel)
        }
    }

    @Test
    fun `builtin novel carries context markers that never appear in single or batch`() {
        languages.forEach { lang ->
            val novel = builtin("novel", lang)
            contextMarkers.forEach { marker ->
                assertTrue("内置 novel[$lang] 缺少 $marker", marker in novel)
            }
            listOf("single", "batch").forEach { group ->
                contextMarkers.forEach { marker ->
                    assertFalse("内置 $group[$lang] 窜入 $marker", marker in builtin(group, lang))
                }
            }
        }
    }

    @Test
    fun `builtin double bracket example appears only in batch`() {
        languages.forEach { lang ->
            assertTrue("内置 batch[$lang] 缺少 [[1]] 示例", "[[1]]" in builtin("batch", lang))
            assertFalse("[[1]]" in builtin("single", lang))
            assertFalse("[[1]]" in builtin("novel", lang))
        }
    }
}
