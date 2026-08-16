package com.piku.client.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchKeywordDao {
    @Query("SELECT * FROM search_keywords ORDER BY searchedAt DESC")
    fun observeAll(): Flow<List<SearchKeywordEntity>>

    /** 同关键词重复搜索时 REPLACE 会删除旧行并写入新行，searchedAt 自然更新为最近一次 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchKeywordEntity)

    @Query("DELETE FROM search_keywords WHERE keyword NOT IN (SELECT keyword FROM search_keywords ORDER BY searchedAt DESC LIMIT :limit)")
    suspend fun prune(limit: Int)

    @Query("DELETE FROM search_keywords WHERE keyword = :keyword")
    suspend fun delete(keyword: String)

    @Query("DELETE FROM search_keywords")
    suspend fun clearAll()
}
