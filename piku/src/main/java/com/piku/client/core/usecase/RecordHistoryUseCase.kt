package com.piku.client.core.usecase

import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.core.data.HistoryRepository
import com.piku.client.core.model.Work
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class RecordHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(work: Work) {
        historyRepository.record(work)
        // 每次写入后顺手清理超出保留期的旧记录
        val retentionDays = settingsRepository.historyRetentionDays.first()
        historyRepository.pruneOlderThan(retentionDays)
    }
}
