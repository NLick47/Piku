package com.piku.client.source.poipiku.data.source

import com.piku.client.core.model.AppError
import com.piku.client.core.model.BookmarkPage
import com.piku.client.core.model.BookmarkVisibility
import com.piku.client.core.model.ImportScope
import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkSource
import com.piku.client.core.network.http.RequestPacer
import com.piku.client.core.network.http.apiCall
import com.piku.client.core.source.RemoteBookmarkSource
import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.source.poipiku.data.remote.NewArrivalParser
import com.piku.client.source.poipiku.data.remote.PoipikuApi
import com.piku.client.source.poipiku.data.repository.AuthRepository
import com.piku.client.source.poipiku.data.repository.PoipikuBookmarkRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PoipikuRemoteBookmarks @Inject constructor(
    private val api: PoipikuApi,
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
    private val bookmarks: PoipikuBookmarkRepository,
    private val pacer: RequestPacer,
) : RemoteBookmarkSource {

    override val source = WorkSource.POIPIKU

    override fun accountKey(): String? = auth.currentUserId()?.toString()

    override suspend fun page(cursor: String?, scope: ImportScope): Result<BookmarkPage> = apiCall {
        val uid = auth.currentUserId() ?: throw AppError.LoginRequired
        val page = cursor?.toIntOrNull() ?: 0
        pacer.await(source)
        val html = api.bookmarks(uid, page).string()
        BookmarkPage(
            items = NewArrivalParser.parse(html),
            nextCursor = if (hasNextPage(html, page)) (page + 1).toString() else null,
        )
    }

    override suspend fun add(work: Work, visibility: BookmarkVisibility?): Result<Boolean> {
        if (visibility == null && !settings.poipikuBookmarkSync.value) {
            return Result.success(false)
        }
        return bookmarks.setBookmarked(work.id, desired = true, pacer = pacer).map { true }
    }

    override suspend fun remove(work: Work): Result<Unit> {
        if (!settings.poipikuBookmarkSync.value) return Result.success(Unit)
        return bookmarks.setBookmarked(work.id, desired = false, pacer = pacer)
    }

    private fun hasNextPage(html: String, current: Int): Boolean =
        PAGE_LINK.findAll(html)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .any { it > current }

    private companion object {
        val PAGE_LINK = Regex("""MyBookmarkListPcV\.jsp\?PG=(\d+)""")
    }
}
