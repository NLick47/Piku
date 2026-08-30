package com.piku.client.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Dns
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WebDavModule {

    /**
     * WebDAV 专用 OkHttpClient：复用主 client 的 DoH DNS 配置，
     * 但不携带 cookie 和 Referer（WebDAV 服务器不需要）。
     */
    @Provides
    @Singleton
    @Named("webdav")
    fun provideWebDavOkHttpClient(dns: Dns): OkHttpClient =
        OkHttpClient.Builder()
            .dns(dns)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

    /**
     * 主 OkHttpClient 的命名引用，供 WebDavSyncRepository 下载 Poipiku 内容时使用。
     */
    @Provides
    @Singleton
    @Named("main")
    fun provideMainClientDelegate(mainClient: OkHttpClient): OkHttpClient = mainClient
}
