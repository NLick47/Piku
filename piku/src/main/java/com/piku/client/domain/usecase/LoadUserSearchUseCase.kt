package com.piku.client.domain.usecase

import com.piku.client.data.repository.FeedRepository
import com.piku.client.domain.model.FollowUser
import javax.inject.Inject

class LoadUserSearchUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(keyword: String, page: Int): Result<List<FollowUser>> =
        feedRepository.getUserSearch(keyword, page)
}
