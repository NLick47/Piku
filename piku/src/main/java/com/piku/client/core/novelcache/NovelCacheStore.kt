package com.piku.client.core.novelcache

import androidx.room.withTransaction
import com.piku.client.core.model.WorkKey
import kotlinx.coroutines.flow.Flow

/** 存储抽象：仓储只依赖它，单测可以用内存实现验证命中/合并逻辑 */
interface NovelCacheStore {
    fun observeAll(): Flow<List<NovelCacheEntity>>

    fun observeTotalBytes(): Flow<Long>

    suspend fun meta(key: WorkKey): NovelCacheEntity?

    suspend fun chapters(key: WorkKey): List<NovelChapterEntity>

    suspend fun digests(key: WorkKey): List<NovelCacheDao.ChapterDigest>

    /** 一次事务写入：元数据 + 变更章 + 截掉多余章 */
    suspend fun write(meta: NovelCacheEntity, upserts: List<NovelChapterEntity>, chapterCount: Int)

    suspend fun delete(key: WorkKey)

    suspend fun clearAll()

    suspend fun totalBytes(): Long
}

internal class RoomNovelCacheStore(
    private val database: NovelCacheDatabase,
    private val dao: NovelCacheDao,
) : NovelCacheStore {

    override fun observeAll(): Flow<List<NovelCacheEntity>> = dao.observeAll()

    override fun observeTotalBytes(): Flow<Long> = dao.observeTotalBytes()

    override suspend fun meta(key: WorkKey): NovelCacheEntity? =
        dao.meta(key.source.name, key.workId)

    override suspend fun chapters(key: WorkKey): List<NovelChapterEntity> =
        dao.chapters(key.source.name, key.workId)

    override suspend fun digests(key: WorkKey): List<NovelCacheDao.ChapterDigest> =
        dao.digests(key.source.name, key.workId)

    override suspend fun write(
        meta: NovelCacheEntity,
        upserts: List<NovelChapterEntity>,
        chapterCount: Int,
    ) {
        database.withTransaction {
            dao.upsertMeta(meta)
            if (upserts.isNotEmpty()) dao.upsertChapters(upserts)
            dao.deleteChaptersFrom(meta.source, meta.workId, chapterCount)
        }
    }

    override suspend fun delete(key: WorkKey) = database.withTransaction {
        dao.deleteWorkChapters(key.source.name, key.workId)
        dao.deleteWork(key.source.name, key.workId)
    }

    override suspend fun clearAll() = database.withTransaction {
        dao.clearAllChapters()
        dao.clearAll()
    }

    override suspend fun totalBytes(): Long = dao.totalBytes()
}
