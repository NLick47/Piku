package com.piku.client.domain.usecase

import com.piku.client.data.repository.FeedRepository
import com.piku.client.domain.model.PopularTag
import javax.inject.Inject

class LoadPopularTagsUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(): Result<List<PopularTag>> =
        feedRepository.getPopularTags()
}