package com.piku.client.domain.usecase

import com.piku.client.data.local.CustomTagRepository
import com.piku.client.domain.model.WorkSource
import javax.inject.Inject

class AddCustomTagUseCase @Inject constructor(
    private val customTagRepository: CustomTagRepository,
) {
    suspend operator fun invoke(source: WorkSource, tag: String): Boolean =
        customTagRepository.addCustomTag(source, tag)
}
