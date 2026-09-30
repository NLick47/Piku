package com.piku.client.di

import android.content.Context
import com.piku.client.data.auth.PixivAuthApi
import com.piku.client.data.auth.PixivAuthEndpoints
import com.piku.client.data.auth.PixivAuthRuntime
import com.piku.client.data.auth.PixivAuthStore
import com.piku.client.data.local.KeystoreCredentialCipher
import com.piku.client.data.local.SharedPreferencesCredentialStorage
import com.piku.client.data.remote.LenientJsonConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.serialization.json.Json
import okhttp3.Call
import retrofit2.Retrofit
import java.util.concurrent.Executors
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PixivAuthModule {

    @Provides
    @Singleton
    fun providePixivAuthEndpoints(): PixivAuthEndpoints = PixivAuthEndpoints()

    @Provides
    @Singleton
    fun providePixivAuthRuntime(): PixivAuthRuntime = PixivAuthRuntime(
        dispatcher = Executors.newSingleThreadExecutor { Thread(it, "piku-pixiv-auth") }
            .asCoroutineDispatcher(),
        now = System::currentTimeMillis,
    )

    @Provides
    @Singleton
    fun providePixivAuthStore(
        @ApplicationContext context: Context,
        cipher: KeystoreCredentialCipher,
        json: Json,
    ): PixivAuthStore = PixivAuthStore(
        storage = SharedPreferencesCredentialStorage(
            context.getSharedPreferences(PIXIV_PREFS_NAME, Context.MODE_PRIVATE),
        ),
        cipher = cipher,
        json = json,
    )

    /** pixiv 自己的加密密钥：别名与 poipiku 的 `piku_credential_key` 不同，密钥材料互不相干 */
    @Provides
    @Singleton
    fun providePixivCredentialCipher(): KeystoreCredentialCipher =
        KeystoreCredentialCipher(PIXIV_KEY_ALIAS)

    /**
     * 换令牌专用 Retrofit：复用 pixiv 的原生传输（DoH + ECH），
     * baseUrl 换成 oauth 域，接口定义与 pixiv 的网页接口互不相干。
     */
    @Provides
    @Singleton
    fun providePixivAuthApi(
        @Named("pixiv") client: Call.Factory,
        json: Json,
        endpoints: PixivAuthEndpoints,
    ): PixivAuthApi = Retrofit.Builder()
        .baseUrl(endpoints.tokenBaseUrl())
        .callFactory(client)
        .addConverterFactory(LenientJsonConverterFactory(json))
        .build()
        .create(PixivAuthApi::class.java)

    internal const val PIXIV_PREFS_NAME = "pixiv_credentials"
    internal const val PIXIV_KEY_ALIAS = "piku_pixiv_auth_key"
}
