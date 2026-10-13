package com.piku.client.source.poipiku.domain.usecase

import com.piku.client.source.poipiku.data.repository.FeedRepository
import com.piku.client.core.model.TagCard
import javax.inject.Inject

class LoadTagSuggestionsUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(tag: String, page: Int): Result<List<TagCard>> =
        feedRepository.getTagSuggestions(tag, page)
}