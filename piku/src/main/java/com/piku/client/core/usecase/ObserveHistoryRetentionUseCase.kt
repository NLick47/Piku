package com.piku.client.core.usecase

import com.piku.client.core.storage.prefs.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveHistoryRetentionUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<Int> = settingsRepository.historyRetentionDays
}
