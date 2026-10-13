package com.piku.client.core.usecase

import com.piku.client.core.data.AdultContentRepository
import javax.inject.Inject

class SetAdultContentUseCase @Inject constructor(
    private val adultContentRepository: AdultContentRepository,
) {
    suspend operator fun invoke(enabled: Boolean): Boolean =
        adultContentRepository.setEnabled(enabled)
}