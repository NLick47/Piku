package com.piku.client.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: Int = 0,
    val categoryCd: Int = 0,
    val tags: String = "",
    val description: String = "",
    val publish: Boolean = true,
    val nsfwWire: Int? = null,
    val visibility: Int = 0,
    val password: String = "",
    val showRecent: Boolean = true,
    val showFirstOnly: Boolean = false,
    val title: String = "",
    val body: String = "",
    val novelDirection: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)
