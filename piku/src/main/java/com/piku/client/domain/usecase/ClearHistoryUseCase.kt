package com.piku.client.domain.usecase

import com.piku.client.data.repository.HistoryRepository
import javax.inject.Inject

class ClearHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
) {
    suspend operator fun invoke() = historyRepository.clear()
}