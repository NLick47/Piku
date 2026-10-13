package com.piku.client.source.poipiku.domain.usecase

import com.piku.client.source.poipiku.data.repository.FeedRepository
import com.piku.client.core.model.FollowUser
import javax.inject.Inject

class LoadUserSearchUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(keyword: String, page: Int): Result<List<FollowUser>> =
        feedRepository.getUserSearch(keyword, page)
}
