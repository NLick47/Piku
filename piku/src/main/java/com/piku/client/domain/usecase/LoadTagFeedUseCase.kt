package com.piku.client.domain.usecase

import com.piku.client.data.repository.FeedRepository
import com.piku.client.domain.model.Work
import javax.inject.Inject

class LoadTagFeedUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(tag: String, page: Int): Result<List<Work>> =
        feedRepository.getTagFeed(tag, page)
}