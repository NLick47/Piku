package com.piku.client.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_keywords")
data class SearchKeywordEntity(
    @PrimaryKey val keyword: String,
    val searchedAt: Long,
)
