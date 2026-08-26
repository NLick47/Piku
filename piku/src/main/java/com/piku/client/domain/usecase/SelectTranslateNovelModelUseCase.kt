package com.piku.client.domain.usecase

import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.translation.ModelEntry
import javax.inject.Inject

/**
 * 选中小说正文翻译模型条目：把地址与模型名一起写进「小说专用」设置。
 * [entry] 为 null 时清空（空串），表示跟随文本翻译模型。
 * 两者成对写入，避免出现"地址是 GLM、模型名是 DeepSeek"的错配。
 */
class SelectTranslateNovelModelUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(entry: ModelEntry?) {
        if (entry == null) {
            settingsRepository.setLlmNovelBaseUrl("")
            settingsRepository.setLlmNovelModel("")
            return
        }
        settingsRepository.setLlmNovelBaseUrl(entry.baseUrl)
        settingsRepository.setLlmNovelModel(entry.model)
    }
}
