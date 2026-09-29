package com.piku.client.di

import android.content.SharedPreferences
import com.piku.client.BuildConfig
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.ApiConfig
import com.piku.client.data.remote.DoHDns
import com.piku.client.data.remote.DoHEventListener
import com.piku.client.data.remote.ImageDiagnostics
import com.piku.client.data.remote.ImageNetworkInterceptor
import com.piku.client.data.remote.ImageRelayInterceptor
import com.piku.client.data.remote.ImageRetryInterceptor
import com.piku.client.data.remote.ImageRouteProbe
import com.piku.client.data.remote.ImageRouteController
import com.piku.client.data.remote.LenientJsonConverterFactory
import com.piku.client.data.remote.NetworkDiagnosis
import com.piku.client.data.remote.NetworkDiagnostics
import com.piku.client.data.remote.NetworkRuntime
import com.piku.client.data.remote.NetworkTuning
import com.piku.client.data.remote.PikuJson
import com.piku.client.data.remote.PoipikuHostnameVerifier
import com.piku.client.data.remote.PoipikuApi
import com.piku.client.data.remote.RefererInterceptor
import com.piku.client.data.remote.RetryInterceptor
import com.piku.client.data.remote.SniStrippingSocketFactory
import com.piku.client.data.remote.UpdateApi
import com.piku.client.data.remote.UploadApi
import com.piku.client.data.remote.pixiv.PixivApi
import com.piku.client.data.remote.pixiv.PixivApiConfig
import com.piku.client.data.remote.translation.LlmChatApi
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
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = PikuJson

    @Provides
    @Singleton
    fun provideDoHDns(prefs: SharedPreferences, diagnostics: NetworkDiagnostics): DoHDns =
        DoHDns(prefs, diagnostics = diagnostics)

    @Provides
    @Singleton
    fun provideDns(doHDns: DoHDns): Dns = doHDns

    @Provides
    @Singleton
    fun provideNetworkDiagnosis(
        doHDns: DoHDns,
        diagnostics: NetworkDiagnostics,
        imageProbe: ImageRouteProbe,
        routeController: ImageRouteController,
        imageDiagnostics: ImageDiagnostics,
    ): NetworkDiagnosis =
        NetworkDiagnosis(doHDns, diagnostics, imageProbe, routeController, imageDiagnostics)

    @Provides
    @Singleton
    fun provideNetworkRuntime(): NetworkRuntime = NetworkRuntime()

    @Provides
    @Singleton
    fun provideNetworkDiagnostics(runtime: NetworkRuntime): NetworkDiagnostics =
        NetworkDiagnostics(runtime)

    @Provides
    @Singleton
    fun provideImageDiagnostics(runtime: NetworkRuntime): ImageDiagnostics = ImageDiagnostics(runtime)

    @Provides
    @Singleton
    fun provideImageRetryInterceptor(
        runtime: NetworkRuntime,
        diagnostics: NetworkDiagnostics,
    ): ImageRetryInterceptor = ImageRetryInterceptor(runtime, diagnostics = diagnostics)

    @Provides
    @Singleton
    fun provideImageRouteProbe(client: OkHttpClient, routeController: ImageRouteController): ImageRouteProbe =
        ImageRouteProbe(
            client.newBuilder()
                .callTimeout(NetworkTuning.PROBE_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build(),
            routeController,
        )

    @Provides
    @Singleton
    fun provideImageRouteController(
        settings: SettingsRepository,
        prefs: SharedPreferences,
        runtime: NetworkRuntime,
    ): ImageRouteController = ImageRouteController(settings, prefs, runtime)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        cookieJar: CookieJar,
        dns: Dns,
        routeController: ImageRouteController,
        runtime: NetworkRuntime,
        diagnostics: NetworkDiagnostics,
    ): OkHttpClient {
        val doHDns = dns as DoHDns
        val sniFactory = SniStrippingSocketFactory()
        val builder = OkHttpClient.Builder()
            .dns(dns)
            .sslSocketFactory(sniFactory, sniFactory.trustManager())
            .hostnameVerifier(PoipikuHostnameVerifier())
            .eventListenerFactory { DoHEventListener(doHDns, diagnostics) }
            .cookieJar(cookieJar)
            .addInterceptor(ImageRelayInterceptor(routeController, diagnostics))
            .addInterceptor(RefererInterceptor())
            .addInterceptor(RetryInterceptor(doHDns, diagnostics = diagnostics))
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(NetworkTuning.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(NetworkTuning.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(NetworkTuning.WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
        }
        return builder.build()
    }

    /**
     * 图片专用 client：复用主 client 的连接池与拦截器（含中转改写），只加一个总超时上限——
     * 坏网下单张图不再因 30s 读超时叠加多次重试而拖到一分钟。
     */
    @Provides
    @Singleton
    @Named("image")
    fun provideImageOkHttpClient(
        client: OkHttpClient,
        imageDiagnostics: ImageDiagnostics,
        runtime: NetworkRuntime,
    ): OkHttpClient =
        client.newBuilder()
            .callTimeout(NetworkTuning.IMAGE_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            // 加在最后：它离网络最近，看到的是中转改写之后的域名
            .addInterceptor(ImageNetworkInterceptor(imageDiagnostics, runtime))
            .build()

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

    /**
     * pixiv 专用 client：**刻意不带** cookieJar / RefererInterceptor / 图片中转。
     * 主 client 会把 poipiku 的会话 cookie 一起发出去，那不该跟到 pixiv。
     * DNS 用系统解析而不是 DoH：alidns 对 pixiv 返回的是投毒结果（Facebook 段 IP），
     * 走 DoH 只会拿到更错的地址——真正干净的出口是自建反代（见 [PixivApiConfig]）。
     */
    @Provides
    @Singleton
    @Named("pixiv")
    fun providePixivOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .dns(Dns.SYSTEM)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", PIXIV_USER_AGENT)
                    .header("Referer", "https://www.pixiv.net/")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(NetworkTuning.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        // 开发期借本机代理出网（见 PixivApiConfig.DEBUG_PROXY）；发布包里这段不生效
        if (BuildConfig.DEBUG) {
            val hostPort = PixivApiConfig.DEBUG_PROXY
            val port = hostPort?.substringAfter(':')?.toIntOrNull()
            if (hostPort != null && port != null) {
                builder.proxy(
                    Proxy(
                        Proxy.Type.HTTP,
                        InetSocketAddress.createUnresolved(hostPort.substringBefore(':'), port),
                    ),
                )
            }
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
    @Named("pixiv")
    fun providePixivRetrofit(@Named("pixiv") client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(PixivApiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(LenientJsonConverterFactory(json))
            .build()

    @Provides
    @Singleton
    fun providePixivApi(@Named("pixiv") retrofit: Retrofit): PixivApi =
        retrofit.create(PixivApi::class.java)

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
     * 上传专用 client：复用主 client 的全部配置（cookie、Referer、UA、DoH 观测），
     * 仅把读写超时拉长到 120s（几十 MB 图片逐张串行，慢网下 30s 会误杀）。
     */
    @Provides
    @Singleton
    @Named("upload")
    fun provideUploadOkHttpClient(client: OkHttpClient): OkHttpClient =
        client.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build()
                chain.proceed(request)
            }
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @Named("upload")
    fun provideUploadRetrofit(
        @Named("upload") client: OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(ApiConfig.BASE_URL)
        .client(client)
        .addConverterFactory(LenientJsonConverterFactory(json))
        .build()

    @Provides
    @Singleton
    fun provideUploadApi(@Named("upload") retrofit: Retrofit): UploadApi =
        retrofit.create(UploadApi::class.java)

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

    /** pixiv 的网页接口对 UA 敏感，用桌面 Chrome 的 UA */
    private const val PIXIV_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
}
