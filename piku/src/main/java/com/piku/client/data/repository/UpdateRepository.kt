package com.piku.client.data.repository

import com.piku.client.BuildConfig
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.GitHubRelease
import com.piku.client.data.remote.UpdateApi
import com.piku.client.data.remote.apiCall
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val updateApi: UpdateApi,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * 检查是否有新版本。
     * @return Result.success(null) 表示已是最新版本
     */
    suspend fun checkForUpdate(): Result<GitHubRelease?> {
        settingsRepository.recordUpdateCheck()
        val result = apiCall { updateApi.getLatestRelease() }
        return result.map { release ->
            val remote = normalizeVersion(release.tagName)
            val local = normalizeVersion(localVersionName())
            // 预发布版本不提示
            if (release.prerelease || remote.isEmpty() || local.isEmpty()) null
            else if (compareVersions(remote, local) > 0) release
            else null
        }
    }

    fun isAutoCheckEnabled(): Boolean = settingsRepository.autoCheckEnabled.value

    fun autoCheckEnabled(): Flow<Boolean> = settingsRepository.autoCheckEnabled

    fun setAutoCheckEnabled(enabled: Boolean) {
        settingsRepository.setAutoCheckEnabled(enabled)
    }

    /** 本地版本号：debug 构建优先用 DEBUG_VERSION_NAME（local.properties 配置），便于测试 */
    private fun localVersionName(): String =
        if (BuildConfig.DEBUG && BuildConfig.DEBUG_VERSION_NAME.isNotBlank()) {
            BuildConfig.DEBUG_VERSION_NAME
        } else {
            BuildConfig.VERSION_NAME
        }

    /** "v1.2.3" -> [1, 2, 3]；debug 后缀（-debug）不参与比较 */
    private fun normalizeVersion(name: String): List<Int> =
        name.trim().removePrefix("v").substringBefore("-").split(".").mapNotNull { it.toIntOrNull() }

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        val len = maxOf(a.size, b.size)
        for (i in 0 until len) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}