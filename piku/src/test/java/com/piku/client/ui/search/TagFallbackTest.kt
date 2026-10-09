package com.piku.client.ui.search

import com.piku.client.domain.model.TagCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 标签建议为空时的兜底判定：输入词改当精确标签，还是留在建议模式 */
class TagFallbackTest {

    private val longTag = "山田リョウ(ぼっち・ざ・ろっく!)"

    @Test
    fun emptySuggestionsFallBackToTheTypedWord() {
        assertEquals(longTag, tagFallbackTarget(longTag, emptyList(), append = false))
    }

    @Test
    fun presentSuggestionsStayInSuggestionMode() {
        val cards = listOf(TagCard(name = "東方Project", thumbnailUrl = null))
        assertNull(tagFallbackTarget("東方", cards, append = false))
    }

    @Test
    fun emptyAppendPageStaysInSuggestionMode() {
        assertNull(tagFallbackTarget("東方", emptyList(), append = true))
    }

    @Test
    fun blankWordStaysInSuggestionMode() {
        assertNull(tagFallbackTarget("", emptyList(), append = false))
    }
}
