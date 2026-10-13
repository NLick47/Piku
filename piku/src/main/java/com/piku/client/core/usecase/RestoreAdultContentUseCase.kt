package com.piku.client.core.usecase

import com.piku.client.core.data.AdultContentRepository
import javax.inject.Inject

class RestoreAdultContentUseCase @Inject constructor(
    private val adultContentRepository: AdultContentRepository,
) {
    suspend operator fun invoke() = adultContentRepository.restoreFromCookie()
}