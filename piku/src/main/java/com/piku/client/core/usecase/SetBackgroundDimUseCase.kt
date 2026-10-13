package com.piku.client.core.usecase

import com.piku.client.core.storage.prefs.SettingsRepository
import javax.inject.Inject

class SetBackgroundDimUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(value: Float, persist: Boolean = false) {
        settingsRepository.setBackgroundDim(value, persist)
    }
}
