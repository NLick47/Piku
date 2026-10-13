package com.piku.client.core.usecase

import com.piku.client.core.storage.prefs.SettingsRepository
import javax.inject.Inject

class SetAiTranslateEnabledUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(enabled: Boolean) {
        settingsRepository.setAiTranslateEnabled(enabled)
    }
}
