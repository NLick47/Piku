package com.piku.client.domain.usecase

import com.piku.client.data.repository.AdultContentRepository
import javax.inject.Inject

class SetAdultContentUseCase @Inject constructor(
    private val adultContentRepository: AdultContentRepository,
) {
    suspend operator fun invoke(enabled: Boolean): Boolean =
        adultContentRepository.setEnabled(enabled)
}