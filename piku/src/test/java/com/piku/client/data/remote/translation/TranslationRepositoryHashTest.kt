package com.piku.client.data.remote.translation

import com.piku.client.domain.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationRepositoryHashTest {

    @Test
    fun `hash is stable and length sensitive`() {
        val h1 = TranslationRepository.hash("テスト")
        val h2 = TranslationRepository.hash("テスト")
        val h3 = TranslationRepository.hash("テスト ")
        assertEquals(h1, h2)
        assertTrue(h1 != h3)
        assertEquals(64, h1.length) // SHA-256 hex
    }

    @Test
    fun `target language follows app language with chinese fallback`() {
        assertEquals("English", TranslationRepository.targetLangName(AppLanguage.EN))
        assertEquals("Japanese", TranslationRepository.targetLangName(AppLanguage.JA))
        // SYSTEM 与 ZH 都按简中处理：本 app 主要受众，且跟随系统时无法确定具体语言
        assertEquals(
            "Simplified Chinese",
            TranslationRepository.targetLangName(AppLanguage.SYSTEM),
        )
        assertEquals(
            "Simplified Chinese",
            TranslationRepository.targetLangName(AppLanguage.ZH),
        )
    }
}
