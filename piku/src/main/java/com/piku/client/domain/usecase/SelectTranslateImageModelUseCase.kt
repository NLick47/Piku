package com.piku.client.domain.usecase

import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.translation.ModelEntry
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
