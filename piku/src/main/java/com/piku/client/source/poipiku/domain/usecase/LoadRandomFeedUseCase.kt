package com.piku.client.source.poipiku.domain.usecase

import com.piku.client.source.poipiku.data.repository.FeedRepository
import com.piku.client.core.model.Work
import javax.inject.Inject

class LoadRandomFeedUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(): Result<List<Work>> =
        feedRepository.getRandomPickups()
}