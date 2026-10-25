package com.piku.client.core.novelcache

import android.content.Context
import androidx.room.Room
import com.piku.client.core.model.WorkKey
import com.piku.client.core.storage.prefs.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NovelCacheModule {

    @Provides
    @Singleton
    fun provideNovelCacheDatabase(@ApplicationContext context: Context): NovelCacheDatabase =
        Room.databaseBuilder(context, NovelCacheDatabase::class.java, "novel_cache.db")
            // 缓存是可重建数据：schema 变化直接清库，不写迁移
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideNovelCacheDao(database: NovelCacheDatabase): NovelCacheDao =
        database.novelCacheDao()

    @Provides
    @Singleton
    fun provideNovelCacheStore(
        database: NovelCacheDatabase,
        dao: NovelCacheDao,
    ): NovelCacheStore = RoomNovelCacheStore(database, dao)

    @Provides
    @Singleton
    fun provideNovelCacheClock(): NovelCacheClock = NovelCacheClock { System.currentTimeMillis() }

    @Provides
    @Singleton
    fun provideNovelProgressAccess(settings: SettingsRepository): NovelProgressAccess =
        object : NovelProgressAccess {
            override fun percent(key: WorkKey): Int = settings.getNovelProgress(key)

            override fun setPercent(key: WorkKey, percent: Int) =
                settings.setNovelProgress(key, percent)
        }
}
