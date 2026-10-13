package com.piku.client.core.usecase

import com.piku.client.core.data.GitHubRelease
import com.piku.client.core.data.UpdateRepository
import javax.inject.Inject

class CheckForUpdateUseCase @Inject constructor(
    private val updateRepository: UpdateRepository,
) {
    suspend operator fun invoke(): Result<GitHubRelease?> = updateRepository.checkForUpdate()
}