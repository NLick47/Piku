package com.piku.client.core.novelcache

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 正文缓存独立成库，版本从 1 起。
 * 缓存是可重建数据：schema 升级直接 destructive，不写迁移、不背迁移测试。
 */
@Database(
    entities = [NovelCacheEntity::class, NovelChapterEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class NovelCacheDatabase : RoomDatabase() {
    abstract fun novelCacheDao(): NovelCacheDao
}
