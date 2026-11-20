package com.piku.client.core.storage.db

import androidx.room.Entity
import androidx.room.Index
import com.piku.client.core.model.WorkSource

@Entity(
    tableName = "bookmark_mirrors",
    primaryKeys = ["source", "account", "workId"],
    indices = [Index("source", "account")],
)
data class BookmarkMirrorEntity(
    val source: WorkSource,
    val account: String,
    val workId: String,
)

data class BookmarkMirrorKeyRow(
    val source: WorkSource,
    val workId: String,
)
