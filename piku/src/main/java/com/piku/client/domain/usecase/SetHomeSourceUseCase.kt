package com.piku.client.domain.usecase

import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.WorkSource
import javax.inject.Inject

class SetHomeSourceUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(value: WorkSource) {
        settingsRepository.setHomeSource(value)
    }
}
