package com.piku.client.domain.usecase

import com.piku.client.data.repository.UpdateRepository
import javax.inject.Inject

class SetAutoCheckEnabledUseCase @Inject constructor(
    private val updateRepository: UpdateRepository,
) {
    operator fun invoke(enabled: Boolean) {
        updateRepository.setAutoCheckEnabled(enabled)
    }
}