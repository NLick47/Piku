package com.piku.client.core.usecase

import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.core.translation.ModelEntry
import javax.inject.Inject

/**
 * 选中图片翻译模型条目：把地址与模型名一起写进「图片专用」设置。
 * [entry] 为 null 时清空，走目录默认。
 */
class SelectTranslateImageModelUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(entry: ModelEntry?) {
        if (entry == null) {
            settingsRepository.setLlmImageBaseUrl("")
            settingsRepository.setLlmImageModel("")
            return
        }
        settingsRepository.setLlmImageBaseUrl(entry.baseUrl)
        settingsRepository.setLlmImageModel(entry.model)
    }
}
