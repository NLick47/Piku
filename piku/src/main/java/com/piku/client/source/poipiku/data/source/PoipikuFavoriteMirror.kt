package com.piku.client.source.poipiku.data.source

import android.util.Log
import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkSource
import com.piku.client.core.source.SourceFavoriteMirror
import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.source.poipiku.data.repository.AuthRepository
import com.piku.client.source.poipiku.data.repository.PoipikuBookmarkRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * poipiku 的收藏镜像：本地收藏成功后再把账号书签收敛到同一状态。
 * 设置关闭或未登录时静默跳过——这不是失败，不能弹同步错误。
 */
@Singleton
class PoipikuFavoriteMirror @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val bookmarkRepository: PoipikuBookmarkRepository,
) : SourceFavoriteMirror {

    override val source = WorkSource.POIPIKU

    override fun accountKey(): String? = authRepository.currentUserId()?.toString()

    override suspend fun mirror(work: Work, favorited: Boolean): Result<Unit> {
        if (!settingsRepository.poipikuBookmarkSync.value) return Result.success(Unit)
        if (!authRepository.isLoggedIn() || authRepository.currentUserId() == null) {
            return Result.success(Unit)
        }
        return bookmarkRepository.setBookmarked(work.id, favorited)
            .onFailure {
                Log.w(TAG, "bookmark mirror failed: work=${work.id} favorited=$favorited", it)
            }
    }

    private companion object {
        const val TAG = "PoipikuFavoriteMirror"
    }
}
