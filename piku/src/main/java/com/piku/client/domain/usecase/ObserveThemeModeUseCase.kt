package com.piku.client.domain.usecase

import com.piku.client.data.local.SettingsRepository
import com.piku.client.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveThemeModeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<ThemeMode> = settingsRepository.themeMode
}

