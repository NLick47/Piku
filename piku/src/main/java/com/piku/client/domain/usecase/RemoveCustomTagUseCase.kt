package com.piku.client.domain.usecase

import com.piku.client.data.local.CustomTagRepository
import com.piku.client.domain.model.WorkSource
import javax.inject.Inject

class RemoveCustomTagUseCase @Inject constructor(
    private val customTagRepository: CustomTagRepository,
) {
    suspend operator fun invoke(source: WorkSource, tag: String) {
        customTagRepository.removeCustomTag(source, tag)
    }
}
