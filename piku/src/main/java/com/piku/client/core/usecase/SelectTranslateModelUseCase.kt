package com.piku.client.core.usecase

import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.core.translation.ModelEntry
import javax.inject.Inject

/**
 * 选中模型条目：把地址与条目 id 一起写进设置。
 * 两者成对写入，避免出现"地址是 GLM、模型名是 DeepSeek"的错配。
 * 存 id 而不是裸模型名：手动 API 之间可能重名，id 才能稳定指向用户选的那一条。
 */
class SelectTranslateModelUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(entry: ModelEntry) {
        settingsRepository.setLlmBaseUrl(entry.baseUrl)
        settingsRepository.setLlmModel(entry.id)
    }
}
