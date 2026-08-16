package com.piku.client.data.repository

import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.ApiConfig
import com.piku.client.data.remote.PoipikuApi
import kotlinx.coroutines.flow.Flow
import java.net.CookieStore
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdultContentRepository @Inject constructor(
    private val api: PoipikuApi,
    private val settingsRepository: SettingsRepository,
    private val cookieStore: CookieStore,
) {

    fun observeEnabled(): Flow<Boolean> = settingsRepository.showAdultContent

    suspend fun restoreFromCookie() {
        val mode = cookieStore.get(URI(ApiConfig.BASE_URL))
            .firstOrNull { it.name == VIEW_MODE_COOKIE }?.value
        settingsRepository.setShowAdultContent(mode == "1")
    }

    suspend fun setEnabled(enabled: Boolean): Boolean {
        val response = api.switchContentsViewMode(if (enabled) 1 else 0)
        if (response.result != 1) return false
        settingsRepository.setShowAdultContent(enabled)
        return true
    }

    private companion object {
        const val VIEW_MODE_COOKIE = "POIPIKU_CONTENTS_VIEW_MODE"
    }
}