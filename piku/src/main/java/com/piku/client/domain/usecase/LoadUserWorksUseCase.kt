package com.piku.client.domain.usecase

import com.piku.client.data.repository.FeedRepository
import com.piku.client.domain.model.UserWorksPage
import javax.inject.Inject

class LoadUserWorksUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(userId: Long, page: Int): Result<UserWorksPage> =
        feedRepository.getUserWorks(userId, page)
}
