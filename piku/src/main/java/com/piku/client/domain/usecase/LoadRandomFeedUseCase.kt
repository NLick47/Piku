package com.piku.client.domain.usecase

import com.piku.client.data.repository.FeedRepository
import com.piku.client.domain.model.Work
import javax.inject.Inject

class LoadRandomFeedUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(): Result<List<Work>> =
        feedRepository.getRandomPickups()
}