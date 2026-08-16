package com.piku.client.domain.usecase

import com.piku.client.data.repository.AdultContentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAdultContentUseCase @Inject constructor(
    private val adultContentRepository: AdultContentRepository,
) {
    operator fun invoke(): Flow<Boolean> = adultContentRepository.observeEnabled()
}