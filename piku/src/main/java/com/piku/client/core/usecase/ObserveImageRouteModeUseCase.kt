package com.piku.client.core.usecase

import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.core.model.ImageRouteMode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveImageRouteModeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<ImageRouteMode> = settingsRepository.imageRouteMode
}
