package com.piku.client.domain.usecase

import com.piku.client.data.local.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveBackgroundDimUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<Float> = settingsRepository.backgroundDim
}
