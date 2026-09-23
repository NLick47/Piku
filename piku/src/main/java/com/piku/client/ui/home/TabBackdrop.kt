package com.piku.client.ui.home

import androidx.compose.ui.graphics.Color
import com.piku.client.data.local.SampledImage

/**
 * 标签行底图取色：把标签行压到的那一小块图片量出来，判深/浅字。
 *
 * 取样区域由当前取景反算（[tabBandSample]）：标签行压在图片的哪一带取决于头部清晰区高度、
 * 图片长宽比、缩放与取景偏移，写死一条横带会错位、也会把带子下方的明暗平均进来。
 */

/** 感知亮度（Rec.709 加权，sRGB 编码值）。判深浅底与测量共用同一套度量 */
internal fun luma(color: Color): Float =
    0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

internal fun luma(argb: Int): Float = luma(
    red = ((argb shr 16) and 0xFF) / 255f,
    green = ((argb shr 8) and 0xFF) / 255f,
    blue = (argb and 0xFF) / 255f,
)

private fun luma(red: Float, green: Float, blue: Float): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue

/**
 * 归一化矩形内的平均亮度：与 [SampledImage] 的像素同口径，判色与取样共用一套度量。
 * 矩形退化成空时返回 null。
 */
internal fun meanLumaOfRect(image: SampledImage?, rect: ImageRect): Float? {
    if (image == null || image.width <= 0 || image.height <= 0) return null
    if (image.pixels.size < image.width * image.height) return null
    val left = (image.width * rect.x0).toInt().coerceIn(0, image.width - 1)
    val right = (image.width * rect.x1).toInt().coerceIn(left + 1, image.width)
    val top = (image.height * rect.y0).toInt().coerceIn(0, image.height - 1)
    val bottom = (image.height * rect.y1).toInt().coerceIn(top + 1, image.height)
    var sum = 0.0
    var count = 0
    for (y in top until bottom) {
        val rowStart = y * image.width
        for (x in left until right) {
            sum += luma(image.pixels[rowStart + x])
            count++
        }
    }
    return if (count == 0) null else (sum / count).toFloat()
}

/** 标签行在窗口坐标里的纵向范围（像素）：头部实测上报，背景层按它反算取样区域 */
internal data class TabBand(val topPx: Float, val bottomPx: Float)

/** 标签行底下那一层的取样目标：图片路径 + 图片归一化矩形 */
internal data class TabBandSample(val path: String, val rect: ImageRect)

/**
 * 标签行底下压着哪张图：头部图盖住就用头部图，否则用毛玻璃层那张
 * （画框式呈现时标签行常压在留白处，那里露出的正是毛玻璃层）。
 */
internal fun tabBandSample(
    bandTop: Float,
    bandBottom: Float,
    heroPath: String?,
    heroFrame: ContentFrame?,
    frostPath: String?,
    frostFrame: ContentFrame?,
): TabBandSample? {
    if (bandBottom <= bandTop) return null
    heroFrame?.let { frame ->
        frame.imageRect(0f, bandTop, frame.viewWidth, bandBottom)?.let { rect ->
            if (heroPath != null) return TabBandSample(heroPath, rect)
        }
    }
    frostFrame?.let { frame ->
        frame.imageRect(0f, bandTop, frame.viewWidth, bandBottom)?.let { rect ->
            if (frostPath != null) return TabBandSample(frostPath, rect)
        }
    }
    return null
}

/**
 * 压暗遮罩的 alpha 分布（视口高度比例 → alpha）：绘制与取样共用同一处，
 * 免得画的是一个渐变、判色时按另一个渐变算。
 * [heroFrac] 是头部清晰区占视口的比例（视差上移后已收窄），绘制侧照此传。
 */
internal fun veilStops(heroFrac: Float, dimBase: Float, midFactor: Float): List<Pair<Float, Float>> =
    listOf(
        0f to dimBase,
        (heroFrac * 0.18f) to dimBase * midFactor,
        (heroFrac + 0.04f).coerceAtMost(1f) to dimBase,
        1f to dimBase * 0.6f,
    )

/** 遮罩在视口高度比例 [t] 处的 alpha：与 [veilStops] 画出的线性渐变逐段一致 */
internal fun veilAlphaAt(stops: List<Pair<Float, Float>>, t: Float): Float {
    if (stops.isEmpty()) return 0f
    val first = stops.first()
    if (t <= first.first) return first.second
    for (i in 1 until stops.size) {
        val (prevT, prevA) = stops[i - 1]
        val (nextT, nextA) = stops[i]
        if (t <= nextT) {
            val span = nextT - prevT
            if (span <= 0f) return nextA
            return prevA + (nextA - prevA) * ((t - prevT) / span)
        }
    }
    return stops.last().second
}

/** 遮罩压过之后的实际底亮度：标签行读的是合成结果，不是原图像素 */
internal fun veiledLuma(imageLuma: Float, veilAlpha: Float, veilColor: Color): Float {
    val alpha = veilAlpha.coerceIn(0f, 1f)
    return imageLuma * (1f - alpha) + luma(veilColor) * alpha
}
