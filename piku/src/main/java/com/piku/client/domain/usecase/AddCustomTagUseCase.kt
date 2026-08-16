package com.piku.client.domain.usecase

import com.piku.client.data.local.CustomTagRepository
import javax.inject.Inject

class AddCustomTagUseCase @Inject constructor(
    private val customTagRepository: CustomTagRepository,
) {
    suspend operator fun invoke(tag: String): Boolean = customTagRepository.addCustomTag(tag)
}
