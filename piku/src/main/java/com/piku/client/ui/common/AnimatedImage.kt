package com.piku.client.ui.common

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.decode.Decoder
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.ImageRequest


private val GIF_URL = Regex("""\.gif(?=_|\?|$)""", RegexOption.IGNORE_CASE)

/**
 * 文件名判定是否为动图。
 */
internal fun isAnimatedImage(url: String?): Boolean =
    url != null && GIF_URL.containsMatchIn(url)

@Composable
internal fun rememberAnimatedImage(url: String?): Any? {
    val context = LocalContext.current
    return remember(context, url) {
        url?.takeIf { it.isNotBlank() }?.let { target ->
            if (!isAnimatedImage(target)) {
                ImageRequest.Builder(context).data(target).build()
            } else {
                ImageRequest.Builder(context)
                    .data(target)
                    .decoderFactory(animatedDecoderFactory())
                    // Coil 的内存缓存键只由 data + size 等组成，不含解码器 ——
                    // 静态首帧与动画会撞同一个键。显式加后缀隔离，否则列表先存进
                    // 去的静态首帧会把这里的动画钉死成不动的图
                    .memoryCacheKey("$target#anim")
                    .build()
            }
        }
    }
}

private fun animatedDecoderFactory(): Decoder.Factory =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        AnimatedImageDecoder.Factory()
    } else {
        GifDecoder.Factory()
    }
