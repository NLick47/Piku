package com.piku.client.core.usecase

import com.piku.client.core.data.HistoryRepository
import com.piku.client.core.model.HistoryItem
import com.piku.client.core.model.HistoryTimeRange
import com.piku.client.core.model.WorkSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
) {
    operator fun invoke(
        range: HistoryTimeRange = HistoryTimeRange.ALL,
        sources: List<WorkSource> = WorkSource.entries,
    ): Flow<List<HistoryItem>> = historyRepository.observeHistory(range, sources)
}