package com.piku.client.di

import com.piku.client.BuildConfig
import com.piku.client.data.remote.PoipikuHostnameVerifier
import com.piku.client.data.remote.SniStrippingSocketFactory
import com.piku.client.data.remote.DoHDns
import com.piku.client.data.remote.ApiConfig
import com.piku.client.data.remote.LenientJsonConverterFactory
import com.piku.client.data.remote.PoipikuApi
import com.piku.client.data.remote.RefererInterceptor
import com.piku.client.data.remote.RetryInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideDns(): Dns = DoHDns()

    @Provides
    @Singleton
    fun provideOkHttpClient(cookieJar: CookieJar, dns: Dns): OkHttpClient {
        val sniFactory = SniStrippingSocketFactory()
        val builder = OkHttpClient.Builder()
            .dns(dns)
            .sslSocketFactory(sniFactory, sniFactory.trustManager())
            .hostnameVerifier(PoipikuHostnameVerifier())
            .cookieJar(cookieJar)
            .addInterceptor(RefererInterceptor())
            .addInterceptor(RetryInterceptor())
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(LenientJsonConverterFactory(json))
            .build()

    @Provides
    @Singleton
    fun providePoipikuApi(retrofit: Retrofit): PoipikuApi = retrofit.create(PoipikuApi::class.java)

    private const val USER_AGENT = "Piku/0.1.0 (Android)"
}
