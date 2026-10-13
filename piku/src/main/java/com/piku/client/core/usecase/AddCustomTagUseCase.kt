package com.piku.client.core.usecase

import com.piku.client.core.storage.repo.CustomTagRepository
import com.piku.client.core.model.WorkSource
import javax.inject.Inject

class AddCustomTagUseCase @Inject constructor(
    private val customTagRepository: CustomTagRepository,
) {
    suspend operator fun invoke(source: WorkSource, tag: String): Boolean =
        customTagRepository.addCustomTag(source, tag)
}
