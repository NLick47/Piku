package com.piku.client.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(
    tableName = "draft_images",
    foreignKeys = [
        ForeignKey(
            entity = DraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("draftId")],
)
data class DraftImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val draftId: Long = 0,
    val path: String = "",
    val sortOrder: Int = 0,
)
