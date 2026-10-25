package com.piku.client.core.novelcache

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelCacheDao {

    @Query("SELECT * FROM novel_cache WHERE source = :source AND workId = :workId")
    suspend fun meta(source: String, workId: String): NovelCacheEntity?

    @Query(
        "SELECT * FROM novel_chapters WHERE source = :source AND workId = :workId " +
            "ORDER BY ordinal ASC",
    )
    suspend fun chapters(source: String, workId: String): List<NovelChapterEntity>

    /** 合并时只需要 hash 与章长，别把几百 KB 正文读回来 */
    @Query(
        "SELECT ordinal, contentHash, charCount FROM novel_chapters " +
            "WHERE source = :source AND workId = :workId ORDER BY ordinal ASC",
    )
    suspend fun digests(source: String, workId: String): List<ChapterDigest>

    @Upsert
    suspend fun upsertMeta(meta: NovelCacheEntity)

    @Upsert
    suspend fun upsertChapters(chapters: List<NovelChapterEntity>)

    @Query(
        "DELETE FROM novel_chapters WHERE source = :source AND workId = :workId " +
            "AND ordinal >= :from",
    )
    suspend fun deleteChaptersFrom(source: String, workId: String, from: Int)

    @Query("DELETE FROM novel_cache WHERE source = :source AND workId = :workId")
    suspend fun deleteWork(source: String, workId: String)

    @Query("DELETE FROM novel_chapters WHERE source = :source AND workId = :workId")
    suspend fun deleteWorkChapters(source: String, workId: String)

    @Query("DELETE FROM novel_cache")
    suspend fun clearAll()

    @Query("DELETE FROM novel_chapters")
    suspend fun clearAllChapters()

    /** 管理页列表：最近缓存的排前面 */
    @Query("SELECT * FROM novel_cache ORDER BY fetchedAt DESC")
    fun observeAll(): Flow<List<NovelCacheEntity>>

    @Query("SELECT COALESCE(SUM(byteSize), 0) FROM novel_cache")
    fun observeTotalBytes(): Flow<Long>

    @Query("SELECT COALESCE(SUM(byteSize), 0) FROM novel_cache")
    suspend fun totalBytes(): Long

    data class ChapterDigest(
        val ordinal: Int,
        val contentHash: String,
        val charCount: Int,
    )
}
