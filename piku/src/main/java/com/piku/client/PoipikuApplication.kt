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
import coil3.serviceLoaderEnabled
import com.piku.client.data.remote.translation.ModelCatalogRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
                    // 这里刻意不注册动画解码器：动图播放走白名单，由
                    // ui.common.rememberAnimatedImage 先用文件名判定（xxx.gif_640.jpg
                    // 里的 .gif 是 poipiku CDN 留下的动图指纹），再单独挂解码器
                    // 默认不播的好处是失败模式安全：漏掉标记只是「该播的没播」，
                    // 一眼能看出也好排查，不会演变成列表满屏动图。
                }
                // 关掉自动注册，否则 coil-gif 会通过 ServiceLoader 自己挂上解码器，
                // 上面的白名单就形同虚设。core 的 BitmapFactoryDecoder 由
                // RealImageLoader 默认装配、不走 ServiceLoader，关掉不影响普通图片的加载
                .serviceLoaderEnabled(false)
                // 关闭 hardware bitmap：此类位图直接由 HWUI/GPU 管理，与渲染线程生命周期
                // 强耦合，在 ColorOS 等 OEM ROM 上会触发 hwuiTask 的
                // "pthread_mutex_lock called on a destroyed mutex" native 崩溃（白屏/闪退）。
                // 改用软件位图绕开该竞争路径（轻微性能开销，换取稳定性）。
                // 动图解码同样依赖此项：GifDecoder 明确拒绝 hardware 位图配置。
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

        // 每次冷启动静默拉取远程模型目录并整体替换当前列表
        // （解密后得到内置免费模型的共享 key，已下架条目随之消失）。
        // 目录只存内存：进程重启即回退无 key 的内置默认，因此必须重新拉取。
        // 失败静默沿用内置默认，下次冷启动自动重试。
        val catalogRepository = entryPoint.modelCatalogRepository()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            catalogRepository.refresh()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        fun okHttpClient(): OkHttpClient

        fun modelCatalogRepository(): ModelCatalogRepository
    }
}
