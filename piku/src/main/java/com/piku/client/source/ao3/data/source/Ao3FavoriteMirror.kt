package com.piku.client.source.ao3.data.source

import com.piku.client.core.model.Ao3BookmarkMirror
import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkSource
import com.piku.client.core.source.SourceFavoriteMirror
import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.source.ao3.data.auth.Ao3Auth
import com.piku.client.source.ao3.data.repository.Ao3BookmarkRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Ao3FavoriteMirror @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val auth: Ao3Auth,
    private val bookmarkRepository: Ao3BookmarkRepository,
) : SourceFavoriteMirror {

    override val source = WorkSource.AO3

    override fun accountKey(): String? = bookmarkRepository.currentAccountKey()

    override suspend fun mirror(work: Work, favorited: Boolean): Result<Unit> {
        val mode = settingsRepository.ao3BookmarkMirror.value
        if (mode == Ao3BookmarkMirror.LOCAL_ONLY) return Result.success(Unit)
        if (!auth.isLoggedIn() || bookmarkRepository.currentAccountKey() == null) {
            return Result.success(Unit)
        }
        return bookmarkRepository.setBookmarked(
            workId = work.id,
            desired = favorited,
            isPrivate = mode == Ao3BookmarkMirror.PRIVATE,
        )
    }
}
