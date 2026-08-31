package com.piku.client.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.piku.client.data.local.AppDatabase
import com.piku.client.data.local.CustomTagRepository
import com.piku.client.data.local.FavoriteDao
import com.piku.client.data.local.FavoriteFolderDao
import com.piku.client.data.local.HistoryDao
import com.piku.client.data.local.PopularTagCacheRepository
import com.piku.client.data.local.SearchKeywordDao
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.local.TranslationDao
import com.piku.client.data.local.WorkPasswordDao
import com.piku.client.data.local.WorkPasswordRepository
import com.piku.client.data.local.CredentialCipher
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
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
            )
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
