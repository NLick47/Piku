package com.piku.client.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.piku.client.core.storage.db.AppDatabase
import com.piku.client.core.storage.repo.CustomTagRepository
import com.piku.client.core.storage.db.FavoriteDao
import com.piku.client.core.storage.db.FavoriteFolderDao
import com.piku.client.core.storage.db.HistoryDao
import com.piku.client.source.poipiku.data.local.PopularTagCacheRepository
import com.piku.client.core.storage.db.SearchKeywordDao
import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.core.storage.db.TranslationDao
import com.piku.client.core.storage.db.WorkPasswordDao
import com.piku.client.source.poipiku.data.local.WorkPasswordRepository
import com.piku.client.core.storage.prefs.CredentialCipher
import com.piku.client.core.storage.db.DraftDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("piku_cache", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "poipiku.db")
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideFavoriteDao(database: AppDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun provideFavoriteFolderDao(database: AppDatabase): FavoriteFolderDao =
        database.favoriteFolderDao()

    @Provides
    fun provideHistoryDao(database: AppDatabase): HistoryDao = database.historyDao()

    @Provides
    fun provideSearchKeywordDao(database: AppDatabase): SearchKeywordDao =
        database.searchKeywordDao()

    @Provides
    fun provideWorkPasswordDao(database: AppDatabase): WorkPasswordDao =
        database.workPasswordDao()

    @Provides
    fun provideTranslationDao(database: AppDatabase): TranslationDao =
        database.translationDao()

    @Provides
    fun provideDraftDao(database: AppDatabase): DraftDao =
        database.draftDao()

    @Provides
    @Singleton
    fun provideWorkPasswordRepository(
        dao: WorkPasswordDao,
        cipher: CredentialCipher,
    ): WorkPasswordRepository = WorkPasswordRepository(dao, cipher)

    @Provides
    @Singleton
    fun provideSettingsRepository(prefs: SharedPreferences): SettingsRepository =
        SettingsRepository(prefs)

    @Provides
    @Singleton
    fun provideCustomTagRepository(prefs: SharedPreferences): CustomTagRepository =
        CustomTagRepository(prefs)

    @Provides
    @Singleton
    fun providePopularTagCacheRepository(prefs: SharedPreferences): PopularTagCacheRepository =
        PopularTagCacheRepository(prefs)
}
