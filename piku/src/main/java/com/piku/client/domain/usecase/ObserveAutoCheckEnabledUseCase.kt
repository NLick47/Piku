package com.piku.client.domain.usecase

import com.piku.client.data.repository.UpdateRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAutoCheckEnabledUseCase @Inject constructor(
    private val updateRepository: UpdateRepository,
) {
    operator fun invoke(): Flow<Boolean> = updateRepository.autoCheckEnabled()
}