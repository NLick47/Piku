package com.piku.client.di

import android.content.SharedPreferences
import com.piku.client.BuildConfig
import com.piku.client.data.remote.ApiConfig
import com.piku.client.data.remote.DoHDns
import com.piku.client.data.remote.LenientJsonConverterFactory
import com.piku.client.data.remote.PoipikuHostnameVerifier
import com.piku.client.data.remote.PoipikuApi
import com.piku.client.data.remote.RefererInterceptor
import com.piku.client.data.remote.RetryInterceptor
import com.piku.client.data.remote.SniStrippingSocketFactory
import com.piku.client.data.remote.UpdateApi
import com.piku.client.data.remote.translation.LlmChatApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Connection
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import javax.net.ssl.SSLException

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
    fun provideDns(prefs: SharedPreferences): Dns = DoHDns(prefs)

    @Provides
    @Singleton
    fun provideOkHttpClient(cookieJar: CookieJar, dns: Dns): OkHttpClient {
        val doHDns = dns as DoHDns
        val sniFactory = SniStrippingSocketFactory()
        val builder = OkHttpClient.Builder()
            .dns(dns)
            .sslSocketFactory(sniFactory, sniFactory.trustManager())
            .hostnameVerifier(PoipikuHostnameVerifier())
            .eventListenerFactory {
                object : EventListener() {
                    private var address: InetAddress? = null

                    override fun connectionAcquired(call: Call, connection: Connection) {
                        address = connection.route().socketAddress.address
                        doHDns.reportSuccess(
                            call.request().url.host,
                            connection.route().socketAddress.address,
                        )
                    }

                    override fun connectFailed(
                        call: Call,
                        inetSocketAddress: InetSocketAddress,
                        proxy: Proxy,
                        protocol: Protocol?,
                        ioe: IOException,
                    ) {
                        doHDns.reportFailure(
                            call.request().url.host,
                            inetSocketAddress.address,
                            if (ioe is SSLException) DoHDns.FailureType.TLS
                            else DoHDns.FailureType.CONNECT,
                        )
                    }

                    override fun responseFailed(call: Call, ioe: IOException) {
                        if (call.isCanceled()) return
                        val host = call.request().url.host
                        address?.let {
                            doHDns.reportFailure(host, it, DoHDns.FailureType.STREAM)
                        }
                    }

                    override fun requestFailed(call: Call, ioe: IOException) {
                        if (call.isCanceled()) return
                        val host = call.request().url.host
                        address?.let {
                            doHDns.reportFailure(host, it, DoHDns.FailureType.STREAM)
                        }
                    }
                }
            }
            .cookieJar(cookieJar)
            .addInterceptor(RefererInterceptor())
            .addInterceptor(RetryInterceptor(doHDns))
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(5, TimeUnit.SECONDS)
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

    @Provides
    @Singleton
    @Named("github")
    fun provideGithubOkHttpClient(dns: Dns): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .dns(dns)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "$USER_AGENT (GitHub)")
                    .header("Accept", "application/vnd.github+json")
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
    @Named("github")
    fun provideGithubRetrofit(@Named("github") client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(GITHUB_API_BASE_URL)
            .client(client)
            .addConverterFactory(LenientJsonConverterFactory(json))
            .build()

    @Provides
    @Singleton
    fun provideUpdateApi(@Named("github") retrofit: Retrofit): UpdateApi = retrofit.create(UpdateApi::class.java)

    /**
     * 翻译专用 client：**刻意不带** RefererInterceptor / cookieJar。
     * 主 client 会给所有请求打上 poipiku 的 Referer 与会话 cookie，
     * 那些东西不能跟着请求发到第三方 LLM 服务上去。
     */
    @Provides
    @Singleton
    @Named("translate")
    fun provideTranslateOkHttpClient(dns: Dns): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .dns(dns)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            // LLM 首字延迟可达数秒，长正文更久，读超时给足
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    // 只打 BASIC：请求头里带 Authorization，绝不能进日志
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
        }
        return builder.build()
    }

    /**
     * 翻译专用 Json：必须 encodeDefaults=true。
     * kotlinx.serialization 默认省略"等于属性默认值"的字段——若复用全局 Json，
     * ChatRequest.temperature(0.2) 会整个从请求里消失，LLM 按服务端默认高温随机发挥，
     * 翻译稳定性无从谈起。
     */
    @Provides
    @Singleton
    @Named("translate")
    fun provideTranslateJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        encodeDefaults = true
    }

    /**
     * 翻译 Retrofit：baseUrl 只是占位，实际地址由 @Url 逐次传入
     * （用户可随时在设置里改成 GLM / DeepSeek / 自建服务）。
     */
    @Provides
    @Singleton
    @Named("translate")
    fun provideTranslateRetrofit(
        @Named("translate") client: OkHttpClient,
        @Named("translate") json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(TRANSLATE_PLACEHOLDER_BASE_URL)
        .client(client)
        .addConverterFactory(LenientJsonConverterFactory(json))
        .build()

    @Provides
    @Singleton
    fun provideLlmChatApi(@Named("translate") retrofit: Retrofit): LlmChatApi =
        retrofit.create(LlmChatApi::class.java)

    private const val GITHUB_API_BASE_URL = "https://api.github.com/"
    private const val TRANSLATE_PLACEHOLDER_BASE_URL = "https://localhost/"
    private const val USER_AGENT = "Piku/0.1.0 (Android)"
}
