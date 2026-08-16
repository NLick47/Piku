package com.piku.client.domain.usecase

import com.piku.client.data.repository.SearchHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSearchHistoryUseCase @Inject constructor(
    private val searchHistoryRepository: SearchHistoryRepository,
) {
    operator fun invoke(): Flow<List<String>> = searchHistoryRepository.observeSearchHistory()
}
