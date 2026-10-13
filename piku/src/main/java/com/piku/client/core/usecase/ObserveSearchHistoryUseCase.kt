package com.piku.client.core.usecase

import com.piku.client.core.data.SearchHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSearchHistoryUseCase @Inject constructor(
    private val searchHistoryRepository: SearchHistoryRepository,
) {
    operator fun invoke(): Flow<List<String>> = searchHistoryRepository.observeSearchHistory()
}
