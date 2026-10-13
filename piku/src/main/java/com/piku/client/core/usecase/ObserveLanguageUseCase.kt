package com.piku.client.core.usecase

import com.piku.client.core.storage.prefs.LanguageStore
import com.piku.client.core.model.AppLanguage
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveLanguageUseCase @Inject constructor(
    private val languageStore: LanguageStore,
) {
    operator fun invoke(): StateFlow<AppLanguage> = languageStore.language
}
