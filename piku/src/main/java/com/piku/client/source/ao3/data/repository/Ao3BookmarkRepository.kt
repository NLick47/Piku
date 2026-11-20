package com.piku.client.source.ao3.data.repository

import android.util.Log
import com.piku.client.core.model.AppError
import com.piku.client.core.network.http.apiCall
import com.piku.client.source.ao3.data.auth.Ao3Auth
import com.piku.client.source.ao3.data.local.Ao3BookmarkSyncStore
import com.piku.client.source.ao3.data.remote.Ao3Api
import com.piku.client.source.ao3.data.remote.Ao3BookmarkHtml
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Ao3BookmarkRepository @Inject constructor(
    private val api: Ao3Api,
    private val auth: Ao3Auth,
    private val syncStore: Ao3BookmarkSyncStore,
) {

    private var lastPageAt = 0L

    fun currentAccountKey(): String? =
        auth.account.value?.account?.takeIf { it.isNotBlank() }

    suspend fun setBookmarked(workId: Long, desired: Boolean, isPrivate: Boolean = true): Result<Unit> =
        apiCall {
            if (!auth.isLoggedIn()) throw AppError.LoginRequired
            val account = currentAccountKey() ?: throw AppError.LoginRequired
            if (desired) {
                if (add(workId, isPrivate)) {
                    syncStore.setMirrored(account, workId, true)
                }
            } else {
                if (!syncStore.isMirrored(account, workId)) return@apiCall
                remove(workId)
                syncStore.setMirrored(account, workId, false)
            }
        }.onFailure { Log.w(TAG, "ao3 bookmark work=$workId desired=$desired failed: $it") }

    private suspend fun add(workId: Long, isPrivate: Boolean): Boolean {
        pace()
        val form = api.newBookmarkForm(workId).string()
        val token = Ao3BookmarkHtml.csrfToken(form) ?: throw AppError.Parse
        val pseudId = Ao3BookmarkHtml.pseudId(form) ?: throw AppError.LoginRequired

        pace()
        val response = api.createBookmark(workId, token, pseudId, if (isPrivate) "1" else "0")
        val path = response.raw().request.url.encodedPath
        val code = response.code()
        response.body()?.close()
        response.errorBody()?.close()
        if (code >= 400) throw AppError.Http(code)
        return when {
            path.startsWith(LOGIN_PATH) -> throw AppError.LoginRequired
            BOOKMARK_SHOW.matches(path) -> true
            path.endsWith("/bookmarks") -> false
            else -> throw AppError.Unknown
        }
    }

    private suspend fun remove(workId: Long) {
        pace()
        val html = api.getWorkBookmarks(workId).string()
        val token = Ao3BookmarkHtml.csrfToken(html) ?: throw AppError.Parse
        val bookmarkId = Ao3BookmarkHtml.ownBookmarkId(html) ?: return

        pace()
        val response = api.deleteBookmark(bookmarkId, token)
        val path = response.raw().request.url.encodedPath
        val code = response.code()
        response.body()?.close()
        response.errorBody()?.close()
        if (path.startsWith(LOGIN_PATH)) throw AppError.LoginRequired
        if (code == 404) return
        if (code >= 400) throw AppError.Http(code)
    }

    private suspend fun pace() {
        val wait = INTERVAL_MS - (System.currentTimeMillis() - lastPageAt)
        if (wait > 0) delay(wait)
        lastPageAt = System.currentTimeMillis()
    }

    private companion object {
        const val TAG = "PikuDiag"
        const val INTERVAL_MS = 1_200L
        const val LOGIN_PATH = "/users/login"

        val BOOKMARK_SHOW = Regex("""^/bookmarks/\d+/?$""")
    }
}
