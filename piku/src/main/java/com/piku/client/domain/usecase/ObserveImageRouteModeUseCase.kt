package com.piku.client.domain.usecase

import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.ImageRouteMode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveImageRouteModeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<ImageRouteMode> = settingsRepository.imageRouteMode
}
