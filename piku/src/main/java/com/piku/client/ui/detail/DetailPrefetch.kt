package com.piku.client.ui.detail

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import com.piku.client.data.repository.ThumbnailResolver

/** 预取时的解码尺寸提示：只影响这次预取自身的解码（见 [rememberWorkDetailPrefetch]） */
private const val PREFETCH_SIDE_PX = 512

/** 原图预取的解码尺寸提示：原图常有几千像素，这里只约束这次预取自身的临时解码 */
private const val FULL_IMAGE_PREFETCH_SIDE_PX = 1440

/**
 * 点击作品卡片时预热详情页首图。
 *
 * 列表卡片渲染的是同一张图的 _360（ui.common.feedThumbUrl），详情页首图是 _640：
 * URL 不同，Coil 缓存互不相通——不预热的话首图一定要重新下一次。点按瞬间按 _640
 * 预热，正好与详情页 HTML / append 请求并行，等内容画出来时图差不多也到了。
 *
 * 只写磁盘缓存（与列表滚动预取同款取舍）：磁盘缓存键就是 URL，与尺寸无关，
 * 所以详情页那次请求照样命中；而内存缓存键会带上目标尺寸，图区尺寸随图片宽高比变化，
 * 预取进内存既对不上键、又白占一块位图。
 *
 * 是否值得预热由 [ThumbnailResolver.detailPrefetchUrl] 判定：空图/占位图（登录墙、
 * 关注墙、密码、R-18、警告）不预热——详情页要么走门卡，要么由 append 出真实图。
 */
@Composable
internal fun rememberWorkDetailPrefetch(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { thumbnailUrl ->
            ThumbnailResolver.detailPrefetchUrl(thumbnailUrl)?.let { url ->
                enqueueDiskPrefetch(context, url, PREFETCH_SIDE_PX)
            }
        }
    }
}

/**
 * 首张原图预取：原图 URL 解析出来后再下，点开查看器时通常已经躺在磁盘里，
 * 不会先看到 640 再"刷"成原图。只预取第一张（最可能被点开的那张），只写磁盘缓存。
 */
@Composable
internal fun rememberFullImagePrefetch(url: String?) {
    val context = LocalContext.current
    LaunchedEffect(context, url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        enqueueDiskPrefetch(context, url, FULL_IMAGE_PREFETCH_SIDE_PX)
    }
}

/** 只写磁盘：磁盘缓存键就是 URL，正式请求照样命中；内存缓存键带目标尺寸，对不上是白占位图 */
private fun enqueueDiskPrefetch(context: Context, url: String, sidePx: Int) {
    SingletonImageLoader.get(context).enqueue(
        ImageRequest.Builder(context)
            .data(url)
            .size(CoilSize(sidePx, sidePx))
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build(),
    )
}
