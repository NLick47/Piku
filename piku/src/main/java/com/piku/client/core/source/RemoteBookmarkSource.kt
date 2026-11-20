package com.piku.client.core.source

import com.piku.client.core.model.BookmarkPage
import com.piku.client.core.model.BookmarkVisibility
import com.piku.client.core.model.ImportScope
import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkSource

interface RemoteBookmarkSource {

    val source: WorkSource

    fun accountKey(): String?

    val supportsVisibilityFilter: Boolean get() = false

    suspend fun page(cursor: String?, scope: ImportScope = ImportScope.ALL): Result<BookmarkPage>

    suspend fun add(work: Work, visibility: BookmarkVisibility? = null): Result<Boolean>

    suspend fun remove(work: Work): Result<Unit>
}
