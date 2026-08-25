package com.piku.client.domain.usecase

import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.translation.ModelEntry
import javax.inject.Inject

/**
 * 选中模型条目：把地址与模型名一起写进设置。
 * 两者成对写入，避免出现"地址是 GLM、模型名是 DeepSeek"的错配。
 */
class SelectTranslateModelUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(entry: ModelEntry) {
        settingsRepository.setLlmBaseUrl(entry.baseUrl)
        settingsRepository.setLlmModel(entry.model)
    }
}
