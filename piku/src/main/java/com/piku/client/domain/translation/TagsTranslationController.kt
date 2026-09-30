package com.piku.client.domain.translation

import com.piku.client.domain.model.AppLanguage
import com.piku.client.domain.model.TranslatedFields
import com.piku.client.domain.model.WorkDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface TagsTranslator {
    /** 只翻标签，返回与 [WorkDetail.tags] 一一对应的译文；全组透传（本就是目标语言）时返回 null */
    suspend fun translateTags(detail: WorkDetail, language: AppLanguage): List<String>?
}

class TagsTranslationController(
    private val repository: TagsTranslator,
    private val scope: CoroutineScope,
    private val read: () -> TagsTranslationState,
    private val write: (transform: (TagsTranslationState) -> TagsTranslationState) -> Unit,
    private val language: () -> AppLanguage,
    private val onFailed: () -> Unit,
) {

    data class TagsTranslationState(
        val detail: WorkDetail? = null,
        val showTranslated: Boolean = false,
        val translating: Boolean = false,
        val override: Boolean? = null,
    )

    fun toggle() {
        val state = read()
        val detail = state.detail ?: return
        if (state.translating || detail.tags.isEmpty()) return
        val toTranslated = !state.showTranslated
        write { it.copy(override = toTranslated) }
        if (!toTranslated || !detail.translated?.tags.isNullOrEmpty()) return
        scope.launch {
            write { it.copy(translating = true) }
            val tags = runCatching { repository.translateTags(detail, language()) }.getOrNull()
            write { s ->
                val current = s.detail ?: return@write s.copy(translating = false)
                val merged = tags
                    ?.let { list -> current.translated?.copy(tags = list) ?: TranslatedFields(tags = list) }
                    ?: current.translated
                s.copy(
                    translating = false,
                    detail = current.copy(translated = merged),
                    // 翻出译文停在译文态；失败保留用户点「译」时的期待态，不冲掉显式选择
                    override = if (tags != null) true else s.override,
                )
            }
            if (tags == null) onFailed()
        }
    }
}
