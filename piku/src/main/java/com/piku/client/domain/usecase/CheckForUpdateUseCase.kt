package com.piku.client.domain.usecase

import com.piku.client.data.remote.GitHubRelease
import com.piku.client.data.repository.UpdateRepository
import javax.inject.Inject

class CheckForUpdateUseCase @Inject constructor(
    private val updateRepository: UpdateRepository,
) {
    suspend operator fun invoke(): Result<GitHubRelease?> = updateRepository.checkForUpdate()
}