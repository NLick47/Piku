package com.piku.client.domain.usecase

import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.repository.HistoryRepository
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
