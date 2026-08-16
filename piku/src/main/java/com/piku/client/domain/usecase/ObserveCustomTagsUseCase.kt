package com.piku.client.domain.usecase

import com.piku.client.data.local.CustomTagRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCustomTagsUseCase @Inject constructor(
    private val customTagRepository: CustomTagRepository,
) {
    operator fun invoke(): Flow<List<String>> = customTagRepository.customTags
}
