package com.piku.client.source.poipiku.domain.usecase

import com.piku.client.source.poipiku.data.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String): Result<Unit> =
        authRepository.login(email, password)
}