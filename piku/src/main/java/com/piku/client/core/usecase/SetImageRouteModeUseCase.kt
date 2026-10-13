package com.piku.client.core.usecase

import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.core.model.ImageRouteMode
import javax.inject.Inject

class SetImageRouteModeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(mode: ImageRouteMode) {
        settingsRepository.setImageRouteMode(mode)
    }
}
