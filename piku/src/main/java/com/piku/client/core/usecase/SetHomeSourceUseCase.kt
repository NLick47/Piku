package com.piku.client.core.usecase

import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.core.model.WorkSource
import javax.inject.Inject

class SetHomeSourceUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(value: WorkSource) {
        settingsRepository.setHomeSource(value)
    }
}
