package com.piku.client.domain.usecase

import com.piku.client.data.local.BackgroundStore
import com.piku.client.data.local.SettingsRepository
import android.net.Uri
import javax.inject.Inject

class SetCustomBackgroundUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backgroundStore: BackgroundStore,
) {
    /** 保存选中的图片为首页背景（含提取的遮罩主色与原始尺寸，并把偏移重置居中）。
     * 成功返回 true，失败返回 false。 */
    suspend operator fun invoke(uri: Uri): Boolean {
        val saved = backgroundStore.saveFromUri(uri) ?: return false
        settingsRepository.setCustomBackgroundPath(
            saved.path, saved.imgWidth, saved.imgHeight
        )
        // 换图时把偏移重置为居中（防止旧图比例不同导致越界）
        settingsRepository.setBackgroundOffset(0f, 0f, persist = true)
        settingsRepository.setBackgroundScrims(saved.scrimDark, saved.scrimLight)
        return true
    }

    /** 保存选中的图片为独立背景层（null 路径的"跟随头部"模式 → 分离模式）。
     * 成功返回 true，失败返回 false。遮罩色不更新，始终以头部图为准。 */
    suspend fun saveBackdrop(uri: Uri): Boolean {
        val saved = backgroundStore.saveBackdropFromUri(uri) ?: return false
        settingsRepository.setBackdropPath(saved.path, saved.imgWidth, saved.imgHeight)
        return true
    }

    /** 清除独立背景层图片，回到跟随头部模式（无缝过渡） */
    suspend fun clearBackdrop() {
        settingsRepository.clearBackdropPath()
    }

    /** 恢复默认背景：清除持久化文件与设置（包括偏移、尺寸与独立背景层）。 */
    suspend fun clear() {
        settingsRepository.setCustomBackgroundPath(null, 0, 0)
        settingsRepository.clearBackdropPath()
        settingsRepository.persistBackgroundOffset()
        backgroundStore.clear()
    }
}
