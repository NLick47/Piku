package com.piku.client.domain.usecase

import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.ImageRouteMode
import javax.inject.Inject

class SetImageRouteModeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(mode: ImageRouteMode) {
        settingsRepository.setImageRouteMode(mode)
    }
}
