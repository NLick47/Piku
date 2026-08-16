package com.piku.client.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface WorkPasswordDao {
    @Query("SELECT password FROM work_passwords WHERE workId = :workId")
    suspend fun getPassword(workId: Long): String?

    @Upsert
    suspend fun upsert(entity: WorkPasswordEntity)

    @Query("DELETE FROM work_passwords WHERE workId = :workId")
    suspend fun delete(workId: Long)
}
