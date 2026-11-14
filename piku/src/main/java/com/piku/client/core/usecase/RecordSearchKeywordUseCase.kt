package com.piku.client.core.usecase

import com.piku.client.core.data.SearchHistoryRepository
import com.piku.client.core.storage.prefs.SettingsRepository
import javax.inject.Inject

class RecordSearchKeywordUseCase @Inject constructor(
    private val searchHistoryRepository: SearchHistoryRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(keyword: String) {
        // 无痕模式：暂停搜索词记录
        if (settingsRepository.incognitoEnabled.value) return
        searchHistoryRepository.record(keyword)
    }
}
