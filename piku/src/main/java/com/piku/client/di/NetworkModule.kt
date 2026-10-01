package com.piku.client.di

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.piku.client.BuildConfig
import com.piku.client.data.auth.PixivAuthStore
import com.piku.client.data.auth.pixivAuthHeaders
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.ApiConfig
import com.piku.client.data.remote.DoHDns
import com.piku.client.data.remote.DoHEventListener
import com.piku.client.data.remote.EchConfigStore
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
import com.piku.client.data.remote.TlsAddressProbe
import com.piku.client.data.remote.UpdateApi
import com.piku.client.data.remote.UploadApi
import com.piku.client.data.remote.ech.EchCallFactory
import com.piku.client.data.remote.pixiv.PixivApi
import com.piku.client.data.remote.pixiv.PixivApiConfig
import com.piku.client.data.remote.pixiv.PixivAppApi
import com.piku.client.data.remote.pixiv.PixivAppConfig
import com.piku.client.data.remote.translation.LlmChatApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = PikuJson

    /**
     * Cloudflare 的共享 ECH 配置：pixiv 的接口直连全靠它（见 piku-ech）。
     * 取配置的通道钉 AliDNS 的 IP，握手不带 SNI，没有可拦的东西。
     * 配置落盘 filesDir：冷启动不再把"现场拉配置"压在首屏请求的关键路径上。
     */
    @Provides
    @Singleton
    fun provideEchConfigStore(
        @ApplicationContext context: Context,
    ): EchConfigStore {
        val file = File(context.filesDir, ECH_CONFIG_FILE)
        return EchConfigStore(
            persist = { config, expiresAt ->
                runCatching {
                    file.writeBytes(config + ByteBuffer.allocate(8).putLong(expiresAt).array())
                }
            },
            restore = {
                runCatching {
                    val all = file.readBytes()
                    if (all.size <= 8) return@runCatching null
                    val expiresAt = ByteBuffer.wrap(all, all.size - 8, 8).long
                    all.copyOfRange(0, all.size - 8) to expiresAt
                }.getOrNull()
            },
            onError = { e ->
                Log.w(TAG, "ech config fetch failed: ${e.javaClass.simpleName}: ${e.message}")
            },
        )
    }

    @Provides
    @Singleton
    fun provideSniSocketFactory(): SniStrippingSocketFactory = SniStrippingSocketFactory()

    @Provides
    @Singleton
    fun provideDoHDns(
        prefs: SharedPreferences,
        diagnostics: NetworkDiagnostics,
        sniFactory: SniStrippingSocketFactory,
    ): DoHDns = DoHDns(prefs, prober = TlsAddressProbe(sniFactory), diagnostics = diagnostics)

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
        sniFactory: SniStrippingSocketFactory,
    ): NetworkDiagnosis =
        NetworkDiagnosis(doHDns, diagnostics, imageProbe, routeController, imageDiagnostics, sniFactory)

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
        routeController: ImageRouteController,
    ): ImageRetryInterceptor =
        ImageRetryInterceptor(runtime, diagnostics = diagnostics, routeController = routeController)

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
        sniFactory: SniStrippingSocketFactory,
    ): OkHttpClient {
        val doHDns = dns as DoHDns
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

    // pixiv 走原生 ECH 通道 刻意不带 cookieJar/Referer/图片中转 免得 poipiku 会话漏过去
    // 鉴权头只对该 app-api 主机生效 pixiv 令牌不漏到网页接口
    @Provides
    @Singleton
    @Named("pixiv")
    fun providePixivCallFactory(
        echConfigStore: EchConfigStore,
        doHDns: DoHDns,
        diagnostics: NetworkDiagnostics,
        sniFactory: SniStrippingSocketFactory,
        pixivAuthStore: PixivAuthStore,
    ): Call.Factory {
        // 开发期借本机代理出网（见 PixivApiConfig.DEBUG_PROXY）：原生通道走不了 HTTP 代理，
        // 这一段保留旧的 OkHttp 路径，发布包里 DEBUG_PROXY 为 null，不生效
        if (BuildConfig.DEBUG) {
            val hostPort = PixivApiConfig.DEBUG_PROXY
            val port = hostPort?.substringAfter(':')?.toIntOrNull()
            if (hostPort != null && port != null) return debugProxyPixivClient(doHDns, diagnostics, sniFactory, hostPort, port)
        }
        return EchCallFactory(
            echConfig = { echConfigStore.currentOrFetch(ECH_CONFIG_WAIT_MS) },
            endpoints = { host ->
                // 静态表优先 其次问自建 DoH 拿到就信 最后才做明文探测兜底
                DoHDns.STATIC_ADDRESSES[host]
                    ?: doHDns.workerResolve(host).takeIf { it.isNotEmpty() }
                    ?: runCatching { doHDns.lookup(host).mapNotNull { it.hostAddress } }
                        .getOrDefault(emptyList())
            },
            userAgent = PIXIV_USER_AGENT,
            authHeaders = { host -> pixivAuthHeaders(host, pixivAuthStore.accessToken()) },
        )
    }

    private fun debugProxyPixivClient(
        doHDns: DoHDns,
        diagnostics: NetworkDiagnostics,
        sniFactory: SniStrippingSocketFactory,
        host: String,
        port: Int,
    ): OkHttpClient = OkHttpClient.Builder()
        .dns(doHDns)
        .sslSocketFactory(sniFactory, sniFactory.trustManager())
        .hostnameVerifier(PoipikuHostnameVerifier())
        .eventListenerFactory { DoHEventListener(doHDns, diagnostics) }
        .addInterceptor(pixivHeaderInterceptor)
        .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port)))
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(NetworkTuning.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val pixivHeaderInterceptor = Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", PIXIV_USER_AGENT)
                .header("Referer", "https://www.pixiv.net/")
                .header("Accept", "application/json")
                .build(),
        )
    }

    @Provides
    @Singleton
    @Named("pixiv")
    fun providePixivRetrofit(@Named("pixiv") client: Call.Factory, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(PixivApiConfig.BASE_URL)
            .callFactory(client)
            .addConverterFactory(LenientJsonConverterFactory(json))
            .build()

    @Provides
    @Singleton
    fun providePixivApi(@Named("pixiv") retrofit: Retrofit): PixivApi =
        retrofit.create(PixivApi::class.java)

    @Provides
    @Singleton
    @Named("pixivApp")
    fun providePixivAppRetrofit(@Named("pixiv") client: Call.Factory, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(PixivAppConfig.BASE_URL)
            .callFactory(client)
            .addConverterFactory(LenientJsonConverterFactory(json))
            .build()

    @Provides
    @Singleton
    fun providePixivAppApi(@Named("pixivApp") retrofit: Retrofit): PixivAppApi =
        retrofit.create(PixivAppApi::class.java)

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
    private val USER_AGENT = ApiConfig.PIKU_USER_AGENT

    private const val TAG = "PikuDiag"

    /** pixiv 的网页接口对 UA 敏感，用桌面 Chrome 的 UA */
    /** 冷启动第一次请求最多等这么久取 ECH 配置 */
    private const val ECH_CONFIG_WAIT_MS = 8_000L

    private const val ECH_CONFIG_FILE = "ech_config.bin"

    private const val PIXIV_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
}
