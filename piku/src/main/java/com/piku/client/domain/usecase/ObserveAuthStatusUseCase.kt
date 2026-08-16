package com.piku.client.domain.usecase

import com.piku.client.data.repository.AuthRepository
import com.piku.client.domain.model.AuthStatus
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveAuthStatusUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    operator fun invoke(): StateFlow<AuthStatus> = authRepository.authStatus
}