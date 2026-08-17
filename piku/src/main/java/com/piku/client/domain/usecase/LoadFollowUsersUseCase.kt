package com.piku.client.domain.usecase

import com.piku.client.data.repository.FeedRepository
import com.piku.client.domain.model.FollowUserPage
import javax.inject.Inject

class LoadFollowUsersUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(page: Int): Result<FollowUserPage> =
        feedRepository.getFollowUsers(page)
}
