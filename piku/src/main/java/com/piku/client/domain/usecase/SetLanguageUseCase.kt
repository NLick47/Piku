package com.piku.client.domain.usecase

import com.piku.client.data.local.LanguageStore
import com.piku.client.domain.model.AppLanguage
import javax.inject.Inject

class SetLanguageUseCase @Inject constructor(
    private val languageStore: LanguageStore,
) {
    operator fun invoke(language: AppLanguage) {
        languageStore.setLanguage(language)
    }
}
