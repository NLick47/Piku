package com.piku.client.source.poipiku.domain.usecase

import com.piku.client.source.poipiku.data.repository.FeedRepository
import com.piku.client.core.model.PopularTag
import javax.inject.Inject

class LoadPopularTagsUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(): Result<List<PopularTag>> =
        feedRepository.getPopularTags()
}