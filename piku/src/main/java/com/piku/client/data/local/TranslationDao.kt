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

    @Upsert
    suspend fun upsertAll(entities: List<TranslationEntity>)
}
