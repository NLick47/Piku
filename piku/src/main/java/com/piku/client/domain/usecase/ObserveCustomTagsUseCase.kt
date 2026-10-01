package com.piku.client.domain.usecase

import com.piku.client.data.local.CustomTagRepository
import com.piku.client.domain.model.WorkSource
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveCustomTagsUseCase @Inject constructor(
    private val customTagRepository: CustomTagRepository,
) {
    operator fun invoke(source: WorkSource): StateFlow<List<String>> =
        customTagRepository.tags(source)
}
