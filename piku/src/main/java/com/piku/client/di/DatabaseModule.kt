package com.piku.client.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.piku.client.data.local.AppDatabase
import com.piku.client.data.local.CustomTagRepository
import com.piku.client.data.local.FavoriteDao
import com.piku.client.data.local.FavoriteFolderDao
import com.piku.client.data.local.HistoryDao
import com.piku.client.data.local.SearchKeywordDao
import com.piku.client.data.local.SettingsRepository
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
    @Singleton
    fun provideWorkPasswordRepository(
        dao: WorkPasswordDao,
        cipher: CredentialCipher,
    ): WorkPasswordRepository = WorkPasswordRepository(dao, cipher)

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("settings") },
        )

    @Provides
    @Singleton
    fun provideSettingsRepository(dataStore: DataStore<Preferences>): SettingsRepository =
        SettingsRepository(dataStore)

    @Provides
    @Singleton
    fun provideCustomTagRepository(dataStore: DataStore<Preferences>): CustomTagRepository =
        CustomTagRepository(dataStore)
}
