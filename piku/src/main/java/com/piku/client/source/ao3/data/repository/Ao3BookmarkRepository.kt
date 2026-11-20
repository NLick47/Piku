package com.piku.client.source.ao3.data.repository

import android.util.Log
import com.piku.client.core.model.AppError
import com.piku.client.core.model.WorkSource
import com.piku.client.core.network.http.RequestPacer
import com.piku.client.core.network.http.apiCall
import com.piku.client.source.ao3.data.auth.Ao3Auth
import com.piku.client.source.ao3.data.remote.Ao3Api
import com.piku.client.source.ao3.data.remote.Ao3BookmarkHtml
import com.piku.client.source.ao3.data.remote.Ao3Html
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Ao3BookmarkRepository @Inject constructor(
    private val api: Ao3Api,
    private val auth: Ao3Auth,
) {

    fun currentAccountKey(): String? =
        auth.account.value?.account?.takeIf { it.isNotBlank() }

    suspend fun add(
        workId: Long,
        isPrivate: Boolean,
        pacer: RequestPacer,
    ): Result<Unit> = apiCall {
        if (!auth.isLoggedIn()) throw AppError.LoginRequired
        pacer.await(WorkSource.AO3)
        val form = api.newBookmarkForm(workId).string()
        val token = Ao3BookmarkHtml.csrfToken(form) ?: throw AppError.Parse
        val pseudId = Ao3BookmarkHtml.pseudId(form) ?: throw AppError.LoginRequired

        pacer.await(WorkSource.AO3)
        val response = api.createBookmark(workId, token, pseudId, if (isPrivate) "1" else "0")
        val path = response.raw().request.url.encodedPath
        val code = response.code()
        val body = response.body()?.string().orEmpty()
        response.errorBody()?.close()
        if (code >= 400) throw AppError.Http(code)
        if (path.startsWith(LOGIN_PATH)) throw AppError.LoginRequired
        val rejected = rejectionReason(body)
        if (rejected != null) throw BookmarkRejected(rejected)
        when {
            BOOKMARK_SHOW.matches(path) -> Unit
            path.endsWith("/bookmarks") ->
                if (!body.contains(ALREADY_BOOKMARKED)) throw BookmarkRejected(extractFlash(body))
            else -> throw AppError.Unknown
        }
    }.onFailure { Log.w(TAG, "ao3 bookmark add work=$workId failed: $it") }

    suspend fun remove(workId: Long, pacer: RequestPacer): Result<Unit> = apiCall {
        if (!auth.isLoggedIn()) throw AppError.LoginRequired
        pacer.await(WorkSource.AO3)
        val html = api.getWorkBookmarks(workId).string()
        val token = Ao3BookmarkHtml.csrfToken(html) ?: throw AppError.Parse
        val bookmarkId = Ao3BookmarkHtml.ownBookmarkId(html) ?: return@apiCall

        pacer.await(WorkSource.AO3)
        val response = api.deleteBookmark(bookmarkId, token)
        val path = response.raw().request.url.encodedPath
        val code = response.code()
        response.body()?.close()
        response.errorBody()?.close()
        if (path.startsWith(LOGIN_PATH)) throw AppError.LoginRequired
        if (code == 404) return@apiCall
        if (code >= 400) throw AppError.Http(code)
    }.onFailure { Log.w(TAG, "ao3 bookmark remove work=$workId failed: $it") }

    private fun extractFlash(html: String): String =
        FLASH.find(html)?.groupValues?.get(1)?.let { Ao3Html.cleanText(it) }.orEmpty()

    private fun rejectionReason(html: String): String? {
        val flash = extractFlash(html)
        if (flash.isBlank()) return null
        if (flash.contains(ALREADY_BOOKMARKED)) return null
        return if (flash.contains(ERROR_MARKER)) flash else null
    }

    class BookmarkRejected(val reason: String) :
        Exception("ao3 bookmark rejected: $reason")

    private companion object {
        const val TAG = "PikuDiag"
        const val LOGIN_PATH = "/users/login"
        const val ALREADY_BOOKMARKED = "You have already bookmarked that"

        const val ERROR_MARKER = "Sorry! We couldn't save this bookmark"

        val BOOKMARK_SHOW = Regex("""^/bookmarks/\d+/?$""")

        val FLASH = Regex("""class="flash[^"]*"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
    }
}
