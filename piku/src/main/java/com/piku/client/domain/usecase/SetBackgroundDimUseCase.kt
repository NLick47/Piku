package com.piku.client.domain.usecase

import com.piku.client.data.local.SettingsRepository
import javax.inject.Inject

class SetBackgroundDimUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(value: Float, persist: Boolean = false) {
        settingsRepository.setBackgroundDim(value, persist)
    }
}
