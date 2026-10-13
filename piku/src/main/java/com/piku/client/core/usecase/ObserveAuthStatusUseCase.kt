package com.piku.client.core.usecase

import com.piku.client.source.poipiku.data.repository.AuthRepository
import com.piku.client.core.model.AuthStatus
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveAuthStatusUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    operator fun invoke(): StateFlow<AuthStatus> = authRepository.authStatus
}