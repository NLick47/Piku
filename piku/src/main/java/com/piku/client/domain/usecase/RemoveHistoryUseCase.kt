package com.piku.client.domain.usecase

import com.piku.client.data.repository.HistoryRepository
import javax.inject.Inject

class RemoveHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
) {
    suspend operator fun invoke(workId: Long) = historyRepository.remove(workId)
}
