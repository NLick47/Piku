package com.piku.client.domain.usecase

import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.repository.HistoryRepository
import com.piku.client.domain.model.Work
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
