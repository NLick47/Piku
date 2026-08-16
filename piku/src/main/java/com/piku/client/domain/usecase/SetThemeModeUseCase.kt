package com.piku.client.domain.usecase

import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.ThemeMode
import javax.inject.Inject

class SetThemeModeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(mode: ThemeMode) {
        settingsRepository.setThemeMode(mode)
    }
}
