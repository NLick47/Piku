package com.piku.client.core.data

import com.piku.client.core.storage.db.SearchKeywordDao
import com.piku.client.core.storage.db.SearchKeywordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryRepository @Inject constructor(
    private val searchKeywordDao: SearchKeywordDao,
) {

    /** 最近搜索关键词，按最近搜索时间倒序（最多 [MAX_KEYWORDS] 条） */
    fun observeSearchHistory(): Flow<List<String>> =
        searchKeywordDao.observeAll().map { list -> list.map { it.keyword } }

    suspend fun record(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        searchKeywordDao.upsert(
            SearchKeywordEntity(
                keyword = trimmed.take(MAX_KEYWORD_LENGTH),
                searchedAt = System.currentTimeMillis(),
            ),
        )
        searchKeywordDao.prune(MAX_KEYWORDS)
    }

    suspend fun remove(keyword: String) {
        searchKeywordDao.delete(keyword)
    }

    suspend fun clear() {
        searchKeywordDao.clearAll()
    }

    private companion object {
        const val MAX_KEYWORDS = 20
        const val MAX_KEYWORD_LENGTH = 200
    }
}
