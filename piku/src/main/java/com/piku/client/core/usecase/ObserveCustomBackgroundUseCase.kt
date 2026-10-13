package com.piku.client.core.usecase

import com.piku.client.core.storage.prefs.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCustomBackgroundUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<String?> = settingsRepository.customBackgroundPath
}
