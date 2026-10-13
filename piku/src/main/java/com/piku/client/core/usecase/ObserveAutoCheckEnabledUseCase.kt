package com.piku.client.core.usecase

import com.piku.client.core.data.UpdateRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAutoCheckEnabledUseCase @Inject constructor(
    private val updateRepository: UpdateRepository,
) {
    operator fun invoke(): Flow<Boolean> = updateRepository.autoCheckEnabled()
}