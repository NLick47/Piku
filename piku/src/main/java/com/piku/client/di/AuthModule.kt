package com.piku.client.di

import android.content.Context
import android.content.SharedPreferences
import com.piku.client.data.local.CredentialCipher
import com.piku.client.data.local.CredentialStore
import com.piku.client.data.local.KeystoreCredentialCipher
import com.piku.client.data.local.SharedPreferencesCredentialStorage
import com.piku.client.data.remote.FileCookieStore
import com.piku.client.data.remote.PersistentCookieJar
import com.piku.client.data.remote.AuthApi
import com.piku.client.data.remote.SessionMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.CookieJar
import retrofit2.Retrofit
import java.io.File
import java.net.CookieStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideCookieStore(@ApplicationContext context: Context): CookieStore =
        FileCookieStore(File(context.filesDir, "cookies.txt"))

    @Provides
    @Singleton
    fun provideCookieJar(cookieStore: CookieStore, sessionMonitor: SessionMonitor): CookieJar =
        PersistentCookieJar(cookieStore, sessionMonitor)

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideCredentialCipher(): CredentialCipher = KeystoreCredentialCipher(KEY_ALIAS)

    @Provides
    @Singleton
    fun provideCredentialStore(
        @ApplicationContext context: Context,
        cipher: CredentialCipher,
    ): CredentialStore {
        val prefs: SharedPreferences =
            context.getSharedPreferences("credentials", Context.MODE_PRIVATE)
        return CredentialStore(SharedPreferencesCredentialStorage(prefs), cipher)
    }

    private const val KEY_ALIAS = "piku_credential_key"
}