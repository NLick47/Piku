package com.piku.client.core.usecase

import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.core.data.HistoryRepository
import javax.inject.Inject

class SetHistoryRetentionUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
) {
    suspend operator fun invoke(days: Int) {
        settingsRepository.setHistoryRetentionDays(days)
        // 设置生效时立即清理超出保留期的旧记录
        historyRepository.pruneOlderThan(days)
    }
}
