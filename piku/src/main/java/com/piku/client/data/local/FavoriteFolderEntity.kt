package com.piku.client.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_folders")
data class FavoriteFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    /** 是否为默认收藏夹（快速收藏的落点，全 App 唯一，不可删除）。 */
    @ColumnInfo(defaultValue = "0") val isDefault: Boolean = false,
)
