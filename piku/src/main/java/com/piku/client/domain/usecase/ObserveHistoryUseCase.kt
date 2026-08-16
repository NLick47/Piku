package com.piku.client.domain.usecase

import com.piku.client.data.repository.HistoryRepository
import com.piku.client.domain.model.HistoryTimeRange
import com.piku.client.domain.model.Work
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
) {
    operator fun invoke(range: HistoryTimeRange = HistoryTimeRange.ALL): Flow<List<Work>> =
        historyRepository.observeHistory(range)
}