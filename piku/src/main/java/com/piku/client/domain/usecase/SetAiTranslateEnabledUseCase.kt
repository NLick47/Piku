package com.piku.client.domain.usecase

import com.piku.client.data.local.SettingsRepository
import javax.inject.Inject

class SetAiTranslateEnabledUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(enabled: Boolean) {
        settingsRepository.setAiTranslateEnabled(enabled)
    }
}
