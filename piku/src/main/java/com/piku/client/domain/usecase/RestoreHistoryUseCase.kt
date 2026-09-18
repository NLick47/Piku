package com.piku.client.domain.usecase

import com.piku.client.data.repository.HistoryRepository
import com.piku.client.domain.model.Work
import javax.inject.Inject

class RestoreHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
) {
    suspend operator fun invoke(work: Work, visitedAt: Long) =
        historyRepository.record(work, visitedAt)
}
