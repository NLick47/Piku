package com.piku.client.source.poipiku.domain.usecase

import com.piku.client.source.poipiku.data.repository.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String, nickname: String): Result<Unit> =
        authRepository.register(email, password, nickname)
}