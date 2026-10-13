package com.piku.client.source.poipiku.domain.usecase

import com.piku.client.source.poipiku.data.repository.FeedRepository
import com.piku.client.core.model.UserWorksPage
import javax.inject.Inject

class LoadUserWorksUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(userId: Long, page: Int): Result<UserWorksPage> =
        feedRepository.getUserWorks(userId, page)
}
