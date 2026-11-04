package com.piku.client.core.translation

import com.piku.client.core.model.AppLanguage
import kotlinx.serialization.Serializable

@Serializable
data class PromptOverrides(
    val single: Map<String, String> = emptyMap(),
    val novel: Map<String, String> = emptyMap(),
) {
    fun value(role: String, langKey: String): String = when (role) {
        Role.NOVEL -> novel[langKey].orEmpty()
        else -> single[langKey].orEmpty()
    }

    fun hasAny(langKey: String? = null): Boolean = if (langKey == null) {
        single.values.any { it.isNotBlank() } || novel.values.any { it.isNotBlank() }
    } else {
        value(Role.TEXT, langKey).isNotBlank() || value(Role.NOVEL, langKey).isNotBlank()
    }

    fun withValue(
        role: String,
        langKey: String,
        value: String,
        defaultValue: String,
    ): PromptOverrides {
        val trimmed = value.trim()
        val next = if (trimmed.isBlank() || trimmed == defaultValue.trim()) {
            remove(role, langKey)
        } else {
            put(role, langKey, trimmed)
        }
        return next
    }

    fun remove(role: String, langKey: String): PromptOverrides = when (role) {
        Role.NOVEL -> copy(novel = novel - langKey)
        else -> copy(single = single - langKey)
    }

    fun normalized(): PromptOverrides = PromptOverrides(
        single = normalize(single),
        novel = normalize(novel),
    )

    fun toPromptSet(): PromptSet? {
        val clean = normalized()
        if (clean.single.isEmpty() && clean.novel.isEmpty()) return null
        val batch = clean.single.mapValues { (langKey, prompt) ->
            val target = targetLang(langKey)
            prompt.trimEnd() + "\n\n" +
                TranslationPrompts.batchInstruction(target) +
                TranslationPrompts.batchExample(target)
        }
        return PromptSet(
            single = clean.single,
            batch = batch,
            novel = clean.novel,
        )
    }

    private fun put(role: String, langKey: String, value: String): PromptOverrides = when (role) {
        Role.NOVEL -> copy(novel = novel + (langKey to value))
        else -> copy(single = single + (langKey to value))
    }

    private fun normalize(values: Map<String, String>): Map<String, String> =
        values.entries
            .filter { it.key in LANGUAGE_KEYS && it.value.isNotBlank() }
            .associate { it.key to it.value.trim() }

    companion object {
        val LANGUAGE_KEYS = setOf("zh", "en", "ja")

        fun langKey(language: AppLanguage): String = when (language) {
            AppLanguage.EN -> "en"
            AppLanguage.JA -> "ja"
            else -> "zh"
        }

        fun targetLang(langKey: String): String = when (langKey) {
            "en" -> LlmTranslateEngine.TARGET_EN
            "ja" -> LlmTranslateEngine.TARGET_JA
            else -> LlmTranslateEngine.TARGET_ZH
        }
    }
}
