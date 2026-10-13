package com.piku.client.core.usecase

import com.piku.client.core.data.HistoryRepository
import com.piku.client.core.model.Work
import javax.inject.Inject

class RestoreHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
) {
    suspend operator fun invoke(work: Work, visitedAt: Long) =
        historyRepository.record(work, visitedAt)
}
