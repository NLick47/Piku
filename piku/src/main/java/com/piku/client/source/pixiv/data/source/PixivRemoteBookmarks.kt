package com.piku.client.source.pixiv.data.source

import com.piku.client.core.model.BookmarkPage
import com.piku.client.core.model.BookmarkVisibility
import com.piku.client.core.model.ImportScope
import com.piku.client.core.model.PixivBookmarkMirror
import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkKind
import com.piku.client.core.model.WorkSource
import com.piku.client.core.network.http.RequestPacer
import com.piku.client.core.source.RemoteBookmarkSource
import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.source.pixiv.data.auth.PixivAuthRepository
import com.piku.client.source.pixiv.data.remote.PixivAppConfig
import com.piku.client.source.pixiv.data.repository.PixivRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PixivRemoteBookmarks @Inject constructor(
    private val repository: PixivRepository,
    private val auth: PixivAuthRepository,
    private val settings: SettingsRepository,
    private val pacer: RequestPacer,
) : RemoteBookmarkSource {

    override val source = WorkSource.PIXIV

    override val supportsVisibilityFilter = true

    override fun accountKey(): String? = auth.currentUserId()?.toString()

    override suspend fun page(cursor: String?, scope: ImportScope): Result<BookmarkPage> {
        val (visibility, stream, id) = decode(cursor, scope)
        pacer.await(source)
        val restrict = if (visibility == PRIVATE) {
            PixivAppConfig.RESTRICT_PRIVATE
        } else {
            PixivAppConfig.RESTRICT_PUBLIC
        }
        return if (stream == NOVEL) {
            repository.ownNovelBookmarks(id, restrict)
                .map { page ->
                    val next = page.nextCursor?.let { "$visibility$SEP$NOVEL$SEP$it" }
                    BookmarkPage(page.items, next ?: nextVisibility(visibility, scope))
                }
        } else {
            repository.ownBookmarks(id, restrict)
                .map { page ->
                    val next = page.nextCursor?.let { "$visibility$SEP$ILLUST$SEP$it" }
                    BookmarkPage(page.items, next ?: "$visibility$SEP$NOVEL$SEP$0")
                }
        }
    }

    override suspend fun add(work: Work, visibility: BookmarkVisibility?): Result<Boolean> {
        if (visibility == null && settings.pixivBookmarkMirror.value == PixivBookmarkMirror.LOCAL_ONLY) {
            return Result.success(false)
        }
        pacer.await(source)
        val restrict = when (visibility) {
            BookmarkVisibility.PUBLIC -> PixivAppConfig.RESTRICT_PUBLIC
            BookmarkVisibility.PRIVATE -> PixivAppConfig.RESTRICT_PRIVATE
            null -> writeRestrict()
        }
        val result = when (work.kind) {
            WorkKind.NOVEL -> repository.bookmarkNovel(work.id, add = true, restrict = restrict)
            WorkKind.ILLUST -> repository.bookmarkIllust(work.id, add = true, restrict = restrict)
        }
        return result.map { true }
    }

    override suspend fun remove(work: Work): Result<Unit> {
        pacer.await(source)
        return when (work.kind) {
            WorkKind.NOVEL -> repository.bookmarkNovel(work.id, add = false)
            WorkKind.ILLUST -> repository.bookmarkIllust(work.id, add = false)
        }
    }

    private fun decode(cursor: String?, scope: ImportScope): Triple<String, String, Long?> {
        if (cursor == null) {
            val first = if (scope == ImportScope.PRIVATE) PRIVATE else PUBLIC
            return Triple(first, ILLUST, null)
        }
        val parts = cursor.split(SEP)
        val visibility = parts.getOrNull(0) ?: PUBLIC
        val stream = parts.getOrNull(1) ?: ILLUST
        val id = parts.getOrNull(2)?.toLongOrNull()?.takeIf { it > 0 }
        return Triple(visibility, stream, id)
    }

    private fun nextVisibility(current: String, scope: ImportScope): String? = when {
        current == PRIVATE -> null
        scope == ImportScope.ALL -> "$PRIVATE$SEP$ILLUST$SEP$0"
        else -> null
    }

    private fun writeRestrict(): String = when (settings.pixivBookmarkMirror.value) {
        PixivBookmarkMirror.PRIVATE -> PixivAppConfig.RESTRICT_PRIVATE
        else -> PixivAppConfig.RESTRICT_PUBLIC
    }

    private companion object {
        const val SEP = ":"
        const val ILLUST = "i"
        const val NOVEL = "n"
        const val PUBLIC = "p"
        const val PRIVATE = "v"
    }
}
