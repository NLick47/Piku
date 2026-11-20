package com.piku.client.source.ao3.data.source

import com.piku.client.core.model.Ao3BookmarkMirror
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
import com.piku.client.source.ao3.data.auth.Ao3Auth
import com.piku.client.source.ao3.data.auth.Ao3AuthStore
import com.piku.client.source.ao3.data.remote.Ao3Api
import com.piku.client.source.ao3.data.remote.Ao3ListingParser
import com.piku.client.source.ao3.data.repository.Ao3BookmarkRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Ao3RemoteBookmarks @Inject constructor(
    private val api: Ao3Api,
    private val auth: Ao3Auth,
    private val store: Ao3AuthStore,
    private val settings: SettingsRepository,
    private val bookmarks: Ao3BookmarkRepository,
    private val pacer: RequestPacer,
) : RemoteBookmarkSource {

    override val source = WorkSource.AO3

    override val supportsVisibilityFilter = true

    override fun accountKey(): String? = store.username()?.takeIf { auth.isLoggedIn() }

    override suspend fun page(cursor: String?, scope: ImportScope): Result<BookmarkPage> = apiCall {
        val user = store.username() ?: throw AppError.LoginRequired
        val page = cursor?.toIntOrNull() ?: 1
        pacer.await(source)
        val html = api.ownBookmarks(user, page = page).string()
        val entries = Ao3ListingParser.parseBookmarkEntries(html)
        BookmarkPage(
            items = entries
                .filter { entry ->
                    when (scope) {
                        ImportScope.ALL -> true
                        ImportScope.PUBLIC -> !entry.isPrivate
                        ImportScope.PRIVATE -> entry.isPrivate
                    }
                }
                .map { it.work },
            nextCursor = if (Ao3ListingParser.hasNextPage(html)) (page + 1).toString() else null,
        )
    }

    override suspend fun add(work: Work, visibility: BookmarkVisibility?): Result<Boolean> {
        if (visibility == null && settings.ao3BookmarkMirror.value == Ao3BookmarkMirror.LOCAL_ONLY) {
            return Result.success(false)
        }
        val isPrivate = when (visibility) {
            BookmarkVisibility.PUBLIC -> false
            BookmarkVisibility.PRIVATE -> true
            null -> settings.ao3BookmarkMirror.value == Ao3BookmarkMirror.PRIVATE
        }
        return bookmarks.add(
            workId = work.id,
            isPrivate = isPrivate,
            pacer = pacer,
        ).map { true }
    }

    override suspend fun remove(work: Work): Result<Unit> =
        bookmarks.remove(workId = work.id, pacer = pacer)
}
