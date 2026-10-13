package com.piku.client.core.usecase

import com.piku.client.core.data.HistoryRepository
import javax.inject.Inject

class ClearHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
) {
    suspend operator fun invoke() = historyRepository.clear()
}