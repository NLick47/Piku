package com.piku.client.domain.usecase

import com.piku.client.data.repository.FeedRepository
import com.piku.client.domain.model.Work
import javax.inject.Inject

class LoadPopularFeedUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(page: Int): Result<List<Work>> =
        feedRepository.getPopularIllusts(page)
}