package com.piku.client.core.usecase

import com.piku.client.core.storage.repo.CustomTagRepository
import com.piku.client.core.model.WorkSource
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveCustomTagsUseCase @Inject constructor(
    private val customTagRepository: CustomTagRepository,
) {
    operator fun invoke(source: WorkSource): StateFlow<List<String>> =
        customTagRepository.tags(source)
}
