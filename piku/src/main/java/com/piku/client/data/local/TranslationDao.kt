package com.piku.client.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface TranslationDao {

    @Query(
        "SELECT translated FROM translations " +
            "WHERE srcHash = :srcHash AND targetLang = :targetLang AND engineId = :engineId",
    )
    suspend fun get(srcHash: String, targetLang: String, engineId: String): String?

    @Query(
        "SELECT srcHash, translated FROM translations " +
            "WHERE srcHash IN (:srcHashes) AND targetLang = :targetLang AND engineId = :engineId",
    )
    suspend fun getAll(srcHashes: List<String>, targetLang: String, engineId: String): List<CacheHit>

    /** 批量缓存读取的中间行（srcHash→译文 一对） */
    data class CacheHit(val srcHash: String, val translated: String)

    @Upsert
    suspend fun upsertAll(entities: List<TranslationEntity>)

    @Query("SELECT COUNT(*) FROM translations")
    suspend fun count(): Int

    /** 按 updatedAt 最旧优先删除 [count] 行（FIFO 淘汰；表含隐式 rowid，Room 默认非 WITHOUT ROWID） */
    @Query(
        "DELETE FROM translations WHERE rowid IN " +
            "(SELECT rowid FROM translations ORDER BY updatedAt ASC LIMIT :count)",
    )
    suspend fun deleteOldest(count: Int)
}
