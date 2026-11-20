package com.piku.client.core.data

import com.piku.client.core.model.BookmarkVisibility
import com.piku.client.core.model.ImportScope
import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkKey
import com.piku.client.core.model.WorkSource
import com.piku.client.core.model.key
import com.piku.client.core.source.RemoteBookmarkSource
import com.piku.client.core.storage.db.BookmarkMirrorDao
import com.piku.client.core.storage.db.BookmarkMirrorEntity
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkMirrorRepository @Inject constructor(
    private val dao: BookmarkMirrorDao,
    sources: Set<@JvmSuppressWildcards RemoteBookmarkSource>,
) {

    private val bySource: Map<WorkSource, RemoteBookmarkSource> = sources.associateBy { it.source }

    private val stateLock = Mutex()
    private val inflight = mutableMapOf<Pair<WorkSource, String>, InflightRefresh>()

    private class InflightRefresh {
        val marked = mutableSetOf<WorkKey>()
        val unmarked = mutableSetOf<WorkKey>()
    }

    fun source(source: WorkSource): RemoteBookmarkSource? = bySource[source]

    fun accountKey(source: WorkSource): String? = bySource[source]?.accountKey()

    fun observe(source: WorkSource, account: String): Flow<Set<WorkKey>> =
        dao.observe(source, account)
            .map { rows -> rows.mapTo(mutableSetOf()) { WorkKey(it.source, it.workId) } }

    suspend fun mirrored(source: WorkSource, account: String): Set<WorkKey> =
        dao.workIds(source, account).mapTo(mutableSetOf()) { WorkKey(source, it) }

    suspend fun mark(source: WorkSource, account: String, keys: Collection<WorkKey>) {
        if (keys.isEmpty()) return
        stateLock.withLock { inflight[source to account]?.marked?.addAll(keys) }
        dao.insertAll(keys.map { BookmarkMirrorEntity(source, account, it.workId) })
    }

    suspend fun unmark(source: WorkSource, account: String, keys: Collection<WorkKey>) {
        if (keys.isEmpty()) return
        stateLock.withLock { inflight[source to account]?.unmarked?.addAll(keys) }
        dao.delete(source, account, keys.map { it.workId })
    }

    suspend fun claimLegacy(source: WorkSource, account: String) {
        dao.claimLegacyRows(source, account)
        dao.dropLegacyRows(source)
    }

    suspend fun syncOne(
        work: Work,
        favorited: Boolean,
        visibility: BookmarkVisibility? = null,
    ): Result<Unit> {
        val remote = bySource[work.source] ?: return Result.success(Unit)
        val account = remote.accountKey() ?: return Result.success(Unit)
        return if (favorited) {
            remote.add(work, visibility).onSuccess { written ->
                if (written) mark(work.source, account, listOf(work.key))
            }.map { }
        } else {
            if (work.key !in mirrored(work.source, account)) return Result.success(Unit)
            remote.remove(work).onSuccess { unmark(work.source, account, listOf(work.key)) }
        }
    }

    suspend fun refresh(source: WorkSource, account: String): Result<Set<WorkKey>> {
        val remote = bySource[source] ?: return Result.failure(NoRemoteBookmarkSource(source))
        val scope = source to account
        val window = InflightRefresh()
        stateLock.withLock { inflight[scope] = window }

        val fetched = mutableSetOf<WorkKey>()
        var cursor: String? = null
        val result = try {
            while (true) {
                val page = remote.page(cursor, ImportScope.ALL).getOrElse { throw it }
                fetched += page.items.map { it.key }
                cursor = page.nextCursor ?: break
            }
            null
        } catch (e: CancellationException) {
            stateLock.withLock { if (inflight[scope] === window) inflight.remove(scope) }
            throw e
        } catch (e: Exception) {
            e
        }

        stateLock.withLock {
            if (inflight[scope] === window) inflight.remove(scope)
            result?.let { return Result.failure(it) }
            fetched += window.marked
            fetched -= window.unmarked
            val keep = fetched.mapTo(mutableSetOf()) { it.workId }
            val stale = dao.workIds(source, account).filterNot { it in keep }
            stale.chunked(SQL_VAR_LIMIT).forEach { chunk ->
                dao.delete(source, account, chunk)
            }
            dao.insertAll(fetched.map { BookmarkMirrorEntity(source, account, it.workId) })
            return Result.success(fetched)
        }
    }

    suspend fun upload(
        source: WorkSource,
        account: String,
        local: List<Work>,
        visibility: BookmarkVisibility,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): UploadResult {
        val remote = bySource[source] ?: run {
            Log.w(TAG, "upload skipped: no adapter for $source")
            return UploadResult(added = 0, failed = local.size, total = local.size)
        }
        val remoteKeys = refresh(source, account).getOrElse { error ->
            Log.w(TAG, "upload refresh failed: source=$source", error)
            return UploadResult(added = 0, failed = local.size, total = local.size)
        }
        val targets = local.filterNot { it.key in remoteKeys }
        if (targets.isEmpty()) {
            onProgress(local.size, local.size)
            return UploadResult(added = 0, failed = 0, total = local.size)
        }
        var done = 0
        var failed = 0
        for (work in targets) {
            if (remote.accountKey() != account) {
                failed += targets.size - done - failed
                break
            }
            remote.add(work, visibility)
                .onSuccess { written ->
                    if (written) {
                        mark(source, account, listOf(work.key))
                        done += 1
                    } else {
                        Log.w(TAG, "upload add skipped by tier: source=$source work=${work.id}")
                        failed += 1
                    }
                }
                .onFailure {
                    Log.w(TAG, "upload add failed: source=$source work=${work.id}", it)
                    failed += 1
                }
            onProgress(done + failed, local.size)
        }
        return UploadResult(added = done, failed = failed, total = local.size)
    }

    suspend fun importInto(
        source: WorkSource,
        account: String,
        scope: ImportScope = ImportScope.ALL,
        onPage: suspend (works: List<Work>, addedAtBase: Long) -> List<WorkKey>,
        onProgress: suspend (scanned: Int, added: Int) -> Unit = { _, _ -> },
    ): ImportResult {
        val remote = bySource[source] ?: return ImportResult(0, 0, emptyList())
        val imported = mutableListOf<WorkKey>()
        var scanned = 0
        var cursor: String? = null
        var stamp = System.currentTimeMillis()
        var failure: Throwable? = null

        while (true) {
            val page = remote.page(cursor, scope).getOrElse { error ->
                if (error is CancellationException) throw error
                failure = error
                break
            }
            if (remote.accountKey() != account) {
                failure = AccountChanged(source, account)
                break
            }
            val added = onPage(page.items, stamp)
            imported += added
            scanned += page.items.size
            stamp -= page.items.size
            onProgress(scanned, imported.size)
            cursor = page.nextCursor ?: break
        }
        return ImportResult(scanned, imported.size, imported, failure)
    }

    data class UploadResult(val added: Int, val failed: Int, val total: Int)

    data class ImportResult(
        val scanned: Int,
        val added: Int,
        val keys: List<WorkKey>,
        val failure: Throwable? = null,
    )

    class NoRemoteBookmarkSource(val source: WorkSource) :
        Exception("no remote bookmark source for $source")

    class AccountChanged(val source: WorkSource, val expected: String) :
        Exception("account changed during $source sync")

    private companion object {
        const val TAG = "BookmarkMirror"

        const val SQL_VAR_LIMIT = 900
    }
}
