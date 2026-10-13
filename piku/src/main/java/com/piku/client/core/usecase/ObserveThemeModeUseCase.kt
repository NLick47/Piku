package com.piku.client.core.usecase

import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveThemeModeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<ThemeMode> = settingsRepository.themeMode
}

