package com.piku.client.core.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.piku.client.core.model.WorkSource
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkMirrorDao {

    @Query("SELECT source, workId FROM bookmark_mirrors WHERE source = :source AND account = :account")
    fun observe(source: WorkSource, account: String): Flow<List<BookmarkMirrorKeyRow>>

    @Query("SELECT workId FROM bookmark_mirrors WHERE source = :source AND account = :account")
    suspend fun workIds(source: WorkSource, account: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<BookmarkMirrorEntity>)

    @Query("DELETE FROM bookmark_mirrors WHERE source = :source AND account = :account AND workId IN (:workIds)")
    suspend fun delete(source: WorkSource, account: String, workIds: List<String>)

    @Query(
        "INSERT OR IGNORE INTO bookmark_mirrors(source, account, workId) " +
            "SELECT :source, :account, workId FROM bookmark_mirrors WHERE source = :source AND account = ''",
    )
    suspend fun claimLegacyRows(source: WorkSource, account: String)

    @Query("DELETE FROM bookmark_mirrors WHERE source = :source AND account = ''")
    suspend fun dropLegacyRows(source: WorkSource)
}
