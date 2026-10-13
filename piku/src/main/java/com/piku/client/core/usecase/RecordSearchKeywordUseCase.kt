package com.piku.client.core.usecase

import com.piku.client.core.data.SearchHistoryRepository
import javax.inject.Inject

class RecordSearchKeywordUseCase @Inject constructor(
    private val searchHistoryRepository: SearchHistoryRepository,
) {
    suspend operator fun invoke(keyword: String) = searchHistoryRepository.record(keyword)
}
