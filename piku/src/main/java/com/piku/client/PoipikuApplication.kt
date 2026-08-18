package com.piku.client

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowHardware
import coil3.request.crossfade
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient

@HiltAndroidApp
class PoipikuApplication : Application() {

    @OptIn(DelicateCoilApi::class)
    override fun onCreate() {
        super.onCreate()
        val entryPoint = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
        SingletonImageLoader.setUnsafe { context ->
            ImageLoader.Builder(context)
                // 与 API 共用同一 OkHttpClient，图片 CDN（cdn.poipiku.com）
                // 同样走防 DNS 污染解析
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { entryPoint.okHttpClient() }))
                }
                // 关闭 hardware bitmap：此类位图直接由 HWUI/GPU 管理，与渲染线程生命周期
                // 强耦合，在 ColorOS 等 OEM ROM 上会触发 hwuiTask 的
                // "pthread_mutex_lock called on a destroyed mutex" native 崩溃（白屏/闪退）。
                // 改用软件位图绕开该竞争路径（轻微性能开销，换取稳定性）。
                .allowHardware(false)
                .crossfade(200)
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("image_cache"))
                        .maxSizeBytes(50L * 1024 * 1024)
                        .build()
                }
                .build()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        fun okHttpClient(): OkHttpClient
    }
}
