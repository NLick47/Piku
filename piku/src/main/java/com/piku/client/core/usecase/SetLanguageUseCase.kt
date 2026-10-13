package com.piku.client.core.usecase

import com.piku.client.core.storage.prefs.LanguageStore
import com.piku.client.core.model.AppLanguage
import javax.inject.Inject

class SetLanguageUseCase @Inject constructor(
    private val languageStore: LanguageStore,
) {
    operator fun invoke(language: AppLanguage) {
        languageStore.setLanguage(language)
    }
}
