package com.piku.client.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC")
    suspend fun all(): List<DraftEntity>

    @Query("SELECT * FROM drafts WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): DraftEntity?

    @Insert
    suspend fun insert(draft: DraftEntity): Long

    @Update
    suspend fun update(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM draft_images WHERE draftId = :draftId ORDER BY sortOrder ASC")
    suspend fun imagesFor(draftId: Long): List<DraftImageEntity>

    @Query("DELETE FROM draft_images WHERE draftId = :draftId")
    suspend fun clearImages(draftId: Long)

    @Insert
    suspend fun insertImages(images: List<DraftImageEntity>)
}
