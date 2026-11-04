package com.piku.client.core.usecase

import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.core.translation.ModelEntry
import javax.inject.Inject

/**
 * 选中小说正文翻译模型条目：把地址与条目 id 一起写进「小说专用」设置。
 * [entry] 为 null 时清空（空串），回落到目录的小说默认。
 * 存 id 而不是裸模型名：手动 API 之间可能重名，id 才能稳定指向用户选的那一条。
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
        settingsRepository.setLlmNovelModel(entry.id)
    }
}
