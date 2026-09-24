package com.piku.client.ui.detail

import com.piku.client.domain.model.TranslatedFields
import com.piku.client.domain.model.WorkDetail
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailUiStateRoleGateTest {

    private fun detail(
        novelText: String = "",
        translated: TranslatedFields? = null,
    ) = WorkDetail(
        title = "作品",
        authorName = "作者",
        authorAvatarUrl = "",
        categoryCd = 0,
        categoryName = "",
        imageUrls = emptyList(),
        tags = emptyList(),
        r18 = false,
        novelText = novelText,
        translated = translated,
    )

    @Test
    fun cachedNovelWithoutModelIsStale() {
        val state = DetailUiState(
            detail = detail(novelText = "原文", translated = TranslatedFields(novelText = "译文")),
            hasNovelModel = false,
        )
        assertTrue(state.novelTranslationStale)
    }

    @Test
    fun novelModelOrNoCachedNovelIsNotStale() {
        val withModel = DetailUiState(
            detail = detail(novelText = "原文", translated = TranslatedFields(novelText = "译文")),
            hasNovelModel = true,
        )
        assertFalse(withModel.novelTranslationStale)

        // 只有短字段译文，正文还没翻过，谈不上历史译文
        val withoutNovelCache = DetailUiState(
            detail = detail(novelText = "原文", translated = TranslatedFields(title = "标题译")),
            hasNovelModel = false,
        )
        assertFalse(withoutNovelCache.novelTranslationStale)
    }
}
