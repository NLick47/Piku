package com.piku.client.domain.usecase

import com.piku.client.data.repository.SearchHistoryRepository
import javax.inject.Inject

class ClearSearchHistoryUseCase @Inject constructor(
    private val searchHistoryRepository: SearchHistoryRepository,
) {
    suspend operator fun invoke() = searchHistoryRepository.clear()
}
