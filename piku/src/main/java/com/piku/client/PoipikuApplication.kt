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
import coil3.size.Precision
import com.piku.client.data.remote.ImageRelayInterceptor
import com.piku.client.data.remote.ImageRouteController
import com.piku.client.data.remote.translation.ModelCatalogRepository
import com.piku.client.data.repository.BlockListSync
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
import okhttp3.Request
import java.util.concurrent.TimeUnit

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
                .precision(Precision.INEXACT)
                .crossfade(200)
                .diskCache {
                    // 200MB：详情页每次打开都会预取首张原图（常见 1~4MB），50MB 装不下几张
                    // 原图就会把列表用的 _360/_640 挤出去，返回列表反而要重新下载。
                    // 目录是 cacheDir，系统存储紧张时可自行回收
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("image_cache"))
                        .maxSizeBytes(200L * 1024 * 1024)
                        .build()
                }
                .build()
        }

        // 冷启动后台刷新目录
        val catalogRepository = entryPoint.modelCatalogRepository()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            catalogRepository.refresh()
        }

        // 屏蔽名单是纯内存的：冷启动后要重新拉一遍，否则首页在本进程内不知道该过滤谁。
        // 取依赖也放 IO 里，不给启动路径加负担
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            entryPoint.blockListSync().start()
        }

        // 探测直连 cdn.poipiku.com 是否可用，决定图片走直连还是中转。
        // 与首屏并行、带 2.5s 上限，不影响启动；结果持久化，图片请求就不必再"先失败一遍"。
        // 仅 AUTO 模式需要探测；手动选了直连/中转的用户直接跳过，省一次请求。
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val controller = entryPoint.imageRouteController()
            if (!controller.shouldRunProbe()) return@launch
            val probeClient = entryPoint.okHttpClient().newBuilder()
                .callTimeout(3000, TimeUnit.MILLISECONDS)
                .build()
            val request = Request.Builder()
                .url(ImageRelayInterceptor.PROBE_URL)
                .head()
                .header(ImageRelayInterceptor.BYPASS_HEADER, "1")
                .build()
            // 只要能拿到任意 HTTP 响应（包括 404）就说明直连链路通，不依赖特定资源存在，
            // 避免资源被删/改名时把全部 AUTO 用户误判为直连不可用、强制走中转。
            val directOk = runCatching {
                probeClient.newCall(request).execute().use { true }
            }.getOrDefault(false)
            controller.applyProbe(directOk)
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        fun okHttpClient(): OkHttpClient

        fun imageRouteController(): ImageRouteController

        fun modelCatalogRepository(): ModelCatalogRepository

        fun blockListSync(): BlockListSync
    }
}
