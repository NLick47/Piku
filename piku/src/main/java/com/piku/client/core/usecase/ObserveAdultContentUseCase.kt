package com.piku.client.core.usecase

import com.piku.client.core.data.AdultContentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAdultContentUseCase @Inject constructor(
    private val adultContentRepository: AdultContentRepository,
) {
    operator fun invoke(): Flow<Boolean> = adultContentRepository.observeEnabled()
}