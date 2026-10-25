package com.piku.client.source.ao3

import android.content.Context
import com.piku.client.core.network.http.LenientJsonConverterFactory
import com.piku.client.core.network.session.FileCookieStore
import com.piku.client.core.network.session.PersistentCookieJar
import com.piku.client.core.network.session.SessionMonitor
import com.piku.client.source.ao3.data.auth.Ao3AuthApi
import com.piku.client.source.ao3.data.remote.Ao3Api
import com.piku.client.source.ao3.data.remote.Ao3Config
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import java.io.File
import java.net.CookieStore
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object Ao3NetworkModule {

    /**
     * AO3 用独立的 cookie 罐：主罐是 poipiku 的会话存储，它登出时 `removeAll()` 会把整罐清空，
     * 共用的话 AO3 的登录会被一起带走。落盘在自己文件里，互不可见。
     */
    @Provides
    @Singleton
    @Named(Ao3Config.CLIENT_NAME)
    fun provideAo3CookieStore(@ApplicationContext context: Context): CookieStore =
        FileCookieStore(File(context.filesDir, "ao3_cookies.txt"))

    /** 会话监听的实例也独立：AO3 的 cookie 不该触发 poipiku 的登录态重建 */
    @Provides
    @Singleton
    @Named(Ao3Config.CLIENT_NAME)
    fun provideAo3CookieJar(@Named(Ao3Config.CLIENT_NAME) store: CookieStore): CookieJar =
        PersistentCookieJar(store, SessionMonitor())

    /**
     * AO3 专用 client：复用主 client 的 DoH/SNI 改写/重试（AO3 的空 SNI 规则在
     * SniStrippingSocketFactory 里生效），但超时按 AO3 的大页面单独调
     * （全文页 400KB+，代理链路下首页就要 7s），cookie 换成自己的罐。
     */
    @Provides
    @Singleton
    @Named(Ao3Config.CLIENT_NAME)
    fun provideAo3Client(
        base: OkHttpClient,
        @Named(Ao3Config.CLIENT_NAME) cookieJar: CookieJar,
    ): OkHttpClient =
        base.newBuilder()
            .connectTimeout(Ao3Config.TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(Ao3Config.TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(Ao3Config.TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .cookieJar(cookieJar)
            .addInterceptor(Ao3RequestHeaders)
            .build()

    /**
     * AO3 的请求头：**Host 必须排在第一个**。
     *
     * Cloudflare 的机器人判定会看 header 顺序：同一个请求（同样的 UA/Accept/Cookie/参数）
     * 带 104.20.8.2 空 SNI 直连，Host 首位回 200、Host 第四位（OkHttp 的默认位置）回
     * 403 `Cf-Mitigated: challenge`（2026-10 实测，两 IP、有无 cookie、有无 ALPN、TLS1.2/1.3
     * 均一致）。所以这里整组头重建：Host 打头，UA/Accept 按站点要的形态，其余（如 Referer）
     * 原序保留——OkHttp 只会在缺 Host 时才自己追加，放在末尾就晚了。
     */
    private object Ao3RequestHeaders : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url
            // 站点只有 https，443 不带端口（与 OkHttp 拼 Host 的规则一致）
            val host = if (url.port == 443) url.host else "${url.host}:${url.port}"
            val headers = Headers.Builder()
                .add("Host", host)
                .add("User-Agent", Ao3Config.USER_AGENT)
                // 站点靠 Accept 协商：OkHttp 默认不发 Accept、浏览器那串又会被判成 HTML，
                // 两者都会 302→/404；只有 */* 回内容（2026-10 实测）
                .add("Accept", "*/*")
                .apply {
                    request.headers.forEach { (name, value) ->
                        when {
                            name.equals("Host", ignoreCase = true) -> Unit
                            name.equals("User-Agent", ignoreCase = true) -> Unit
                            name.equals("Accept", ignoreCase = true) -> Unit
                            else -> add(name, value)
                        }
                    }
                }
                .build()
            return chain.proceed(request.newBuilder().headers(headers).build())
        }
    }

    @Provides
    @Singleton
    fun provideAo3Api(@Named(Ao3Config.CLIENT_NAME) client: OkHttpClient, json: Json): Ao3Api =
        Retrofit.Builder()
            .baseUrl(Ao3Config.BASE_URL)
            .client(client)
            .addConverterFactory(LenientJsonConverterFactory(json))
            .build()
            .create(Ao3Api::class.java)

    /**
     * 登录链专用：关闭重定向跟随。成功与失败都是 302，要靠 Location 分辨
     * `/users/<name>` 与 `/auth_error`，跟随后就只剩一页 HTML 看不出来了。
     */
    @Provides
    @Singleton
    fun provideAo3AuthApi(
        @Named(Ao3Config.CLIENT_NAME) client: OkHttpClient,
        json: Json,
    ): Ao3AuthApi = Retrofit.Builder()
        .baseUrl(Ao3Config.BASE_URL)
        .client(client.newBuilder().followRedirects(false).build())
        .addConverterFactory(LenientJsonConverterFactory(json))
        .build()
        .create(Ao3AuthApi::class.java)
}
