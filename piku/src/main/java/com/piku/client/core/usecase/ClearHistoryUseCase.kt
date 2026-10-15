package com.piku.client.core.usecase

import com.piku.client.core.data.HistoryRepository
import com.piku.client.core.model.WorkSource
import javax.inject.Inject

class ClearHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
) {
    /** [sources] 是资料库池：默认全源，页面按当前池传入 */
    suspend operator fun invoke(sources: List<WorkSource> = WorkSource.entries) =
        historyRepository.clear(sources)
}