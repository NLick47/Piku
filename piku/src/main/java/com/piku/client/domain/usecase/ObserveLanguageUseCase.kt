package com.piku.client.domain.usecase

import com.piku.client.data.local.LanguageStore
import com.piku.client.domain.model.AppLanguage
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveLanguageUseCase @Inject constructor(
    private val languageStore: LanguageStore,
) {
    operator fun invoke(): StateFlow<AppLanguage> = languageStore.language
}
