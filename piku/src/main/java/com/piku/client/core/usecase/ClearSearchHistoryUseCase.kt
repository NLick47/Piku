package com.piku.client.core.usecase

import com.piku.client.core.data.SearchHistoryRepository
import javax.inject.Inject

class ClearSearchHistoryUseCase @Inject constructor(
    private val searchHistoryRepository: SearchHistoryRepository,
) {
    suspend operator fun invoke() = searchHistoryRepository.clear()
}
