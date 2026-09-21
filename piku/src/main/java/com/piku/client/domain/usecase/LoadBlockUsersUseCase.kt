package com.piku.client.domain.usecase

import com.piku.client.data.repository.FeedRepository
import com.piku.client.domain.model.FollowUser
import javax.inject.Inject

class LoadBlockUsersUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(page: Int): Result<List<FollowUser>> =
        feedRepository.getBlockUsers(page)
}
