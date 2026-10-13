package com.piku.client.core.usecase

import com.piku.client.core.translation.TranslationRepository
import javax.inject.Inject

class TranslateSearchKeywordUseCase @Inject constructor(
    private val translationRepository: TranslationRepository,
) {
    suspend operator fun invoke(raw: String, toJapanese: Boolean): String? {
        val trimmed = raw.trim()
        val prefix = when {
            trimmed.startsWith("#") -> "#"
            trimmed.startsWith("@") -> "@"
            else -> ""
        }
        val body = trimmed.removePrefix("#").removePrefix("@").trim()
        if (body.isEmpty()) return null
        val translated = translationRepository.translateSearchKeyword(body, toJapanese) ?: return null
        return prefix + translated
    }
}
