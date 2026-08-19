package com.piku.client.domain.usecase

import com.piku.client.data.repository.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String, nickname: String): Result<Unit> =
        authRepository.register(email, password, nickname)
}