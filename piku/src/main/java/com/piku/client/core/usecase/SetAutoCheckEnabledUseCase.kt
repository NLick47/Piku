package com.piku.client.core.usecase

import com.piku.client.core.data.UpdateRepository
import javax.inject.Inject

class SetAutoCheckEnabledUseCase @Inject constructor(
    private val updateRepository: UpdateRepository,
) {
    operator fun invoke(enabled: Boolean) {
        updateRepository.setAutoCheckEnabled(enabled)
    }
}