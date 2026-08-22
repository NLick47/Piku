package com.piku.client.domain.usecase

import com.piku.client.data.local.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCustomBackgroundUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<String?> = settingsRepository.customBackgroundPath
}
