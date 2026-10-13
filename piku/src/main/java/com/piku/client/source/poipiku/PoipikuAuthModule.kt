package com.piku.client.source.poipiku

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.piku.client.core.storage.prefs.CredentialCipher
import com.piku.client.core.storage.prefs.CredentialStore
import com.piku.client.core.storage.prefs.KeystoreCredentialCipher
import com.piku.client.core.storage.prefs.SharedPreferencesCredentialStorage
import com.piku.client.core.network.session.FileCookieStore
import com.piku.client.core.network.session.PersistentCookieJar
import com.piku.client.source.poipiku.data.remote.AuthApi
import com.piku.client.core.network.session.SessionMonitor
import com.piku.client.source.poipiku.data.repository.SessionRuntime
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.asCoroutineDispatcher
import okhttp3.CookieJar
import retrofit2.Retrofit
import java.io.File
import java.net.CookieStore
import java.util.concurrent.Executors
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PoipikuAuthModule {

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
    fun provideSessionRuntime(): SessionRuntime = SessionRuntime(
        dispatcher = Executors.newSingleThreadExecutor { Thread(it, "piku-session") }
            .asCoroutineDispatcher(),
        now = SystemClock::elapsedRealtime,
    )

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