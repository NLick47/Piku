package com.piku.client.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavoriteEntity::class,
        FavoriteFolderEntity::class,
        FavoriteMembershipEntity::class,
        HistoryEntity::class,
        SearchKeywordEntity::class,
        WorkPasswordEntity::class,
        TranslationEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun favoriteFolderDao(): FavoriteFolderDao
    abstract fun historyDao(): HistoryDao
    abstract fun searchKeywordDao(): SearchKeywordDao
    abstract fun workPasswordDao(): WorkPasswordDao
    abstract fun translationDao(): TranslationDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE favorites ADD COLUMN authorId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE favorites ADD COLUMN thumbnailUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE favorites ADD COLUMN authorAvatarUrl TEXT")
                db.execSQL("ALTER TABLE favorites ADD COLUMN imageCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE favorites ADD COLUMN r18 INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS history (" +
                        "workId TEXT NOT NULL PRIMARY KEY, " +
                        "authorId INTEGER NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "authorName TEXT NOT NULL, " +
                        "authorAvatarUrl TEXT, " +
                        "thumbnailUrl TEXT NOT NULL, " +
                        "imageCount INTEGER NOT NULL, " +
                        "r18 INTEGER NOT NULL, " +
                        "visitedAt INTEGER NOT NULL)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS favorite_folders (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS favorite_memberships (" +
                        "folderId INTEGER NOT NULL, " +
                        "workId TEXT NOT NULL, " +
                        "addedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(folderId, workId), " +
                        "FOREIGN KEY(folderId) REFERENCES favorite_folders(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(workId) REFERENCES favorites(workId) ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_memberships_folderId ON favorite_memberships(folderId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_memberships_workId ON favorite_memberships(workId)")
                db.execSQL(
                    "INSERT INTO favorite_folders(name, createdAt) " +
                        "VALUES('默认收藏夹', ${System.currentTimeMillis()})",
                )
                db.execSQL(
                    "INSERT INTO favorite_memberships(folderId, workId, addedAt) " +
                        "SELECT (SELECT id FROM favorite_folders ORDER BY id LIMIT 1), workId, addedAt FROM favorites",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS search_keywords (" +
                        "keyword TEXT NOT NULL PRIMARY KEY, " +
                        "searchedAt INTEGER NOT NULL)",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS work_passwords (" +
                        "workId INTEGER NOT NULL PRIMARY KEY, " +
                        "password TEXT NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)",
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 默认收藏夹显式化：新增 isDefault 标记。
                // 迁移时把最早创建的收藏夹（即旧逻辑的“默认夹”）标记为默认。
                db.execSQL(
                    "ALTER TABLE favorite_folders ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "UPDATE favorite_folders SET isDefault = 1 " +
                        "WHERE id = (SELECT id FROM favorite_folders ORDER BY createdAt ASC, id ASC LIMIT 1)",
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // AI 翻译译文缓存：(原文哈希, 目标语言, 引擎) 三元组为主键，
                // 换模型/换语言互不污染，纯缓存表，丢失只会重新翻译。
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS translations (" +
                        "srcHash TEXT NOT NULL, " +
                        "targetLang TEXT NOT NULL, " +
                        "engineId TEXT NOT NULL, " +
                        "translated TEXT NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(srcHash, targetLang, engineId))",
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_translations_updatedAt ON translations(updatedAt)")
            }
        }
    }
}