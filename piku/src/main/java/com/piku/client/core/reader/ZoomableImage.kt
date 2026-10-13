package com.piku.client.core.reader
import com.piku.client.core.reader.ViewerImage

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.piku.client.core.ui.common.rememberAnimatedImage
import com.piku.client.core.ui.theme.PikuColors
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.delay
import kotlin.math.abs

internal const val MIN_SCALE = 1f
internal const val MAX_SCALE = 4f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val SCALE_EPSILON = 0.01f

/** 点按热区：左右各三分之一翻页，中间留给显隐与双击缩放 */
internal const val TAP_ZONE_LEFT = 0
internal const val TAP_ZONE_CENTER = 1
internal const val TAP_ZONE_RIGHT = 2

/** 点按热区的三等分：区域提示的色带宽度共用它，改这里两处一起变 */
internal const val TAP_ZONE_DIVISOR = 3f

internal fun tapZone(offset: Offset, viewport: IntSize): Int = when {
    viewport.width <= 0 -> TAP_ZONE_CENTER
    offset.x < viewport.width / TAP_ZONE_DIVISOR -> TAP_ZONE_LEFT
    offset.x > viewport.width * 2f / TAP_ZONE_DIVISOR -> TAP_ZONE_RIGHT
    else -> TAP_ZONE_CENTER
}

/** 原图淡入时长（ImageLoader 的 crossfade 是 200ms，留一点余量） */
private const val FULL_IMAGE_FADE_MS = 250L

/** 缩放状态（纯数据，便于测试） */
data class ZoomState(val scale: Float = MIN_SCALE, val offset: Offset = Offset.Zero)

/**
 * 纯缩放/平移变换计算，与 UI 无关，便于单测。
 */
object ZoomTransform {

    /** 缩放时以 pivot 为锚点保持手指位置不动 */
    fun zoomTo(
        current: ZoomState,
        targetScale: Float,
        pivot: Offset,
        viewport: IntSize,
    ): ZoomState {
        if (!targetScale.isFinite()) return current
        val clamped = targetScale.coerceIn(MIN_SCALE, MAX_SCALE)
        if (clamped == current.scale) return current
        if (clamped <= MIN_SCALE + SCALE_EPSILON) return ZoomState(MIN_SCALE, Offset.Zero)
        val k = clamped / current.scale
        return ZoomState(
            scale = clamped,
            offset = clampOffset(pivot - (pivot - current.offset) * k, clamped, viewport),
        )
    }

    /** 双指捏合/单指拖动：以 centroid 为锚点缩放（手指下的内容点不动），pan 为屏幕位移 */
    fun transform(
        current: ZoomState,
        zoomChange: Float,
        panChange: Offset,
        centroid: Offset,
        viewport: IntSize,
    ): ZoomState {
        // 第二根手指按下的瞬间上一帧距离为 0，calculateZoom 可能产生 Inf/NaN，跳过该帧
        if (!zoomChange.isFinite()) return current
        val newScale = (current.scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        if (!newScale.isFinite()) return current
        if (newScale <= MIN_SCALE + SCALE_EPSILON) return ZoomState(MIN_SCALE, Offset.Zero)
        val k = newScale / current.scale
        val targetOffset = current.offset * k + centroid * (1f - k) + panChange
        return ZoomState(
            scale = newScale,
            offset = clampOffset(targetOffset, newScale, viewport),
        )
    }

    /** 缩放时边界约束：围绕左上角原点缩放时，内容可平移范围为 [w*(1-scale), 0]（保证盖满屏幕） */
    fun clampOffset(target: Offset, scale: Float, viewport: IntSize): Offset {
        if (!target.x.isFinite() || !target.y.isFinite()) return Offset.Zero
        if (scale <= MIN_SCALE + SCALE_EPSILON) return Offset.Zero
        val minX = viewport.width * (1f - scale)
        val minY = viewport.height * (1f - scale)
        return Offset(
            target.x.coerceIn(minX, 0f),
            target.y.coerceIn(minY, 0f),
        )
    }
}

/**
 * 单页查看器图片：
 * - 缩略图常驻打底（Coil 缓存秒显），原图经 SubcomposeAsyncImage 就绪后无闪覆盖，避免空白
 * - 手势：1x 单指拖动不消费事件（留给 pager 翻页）；第二根手指按下即消费，捏合优先于翻页；
 *   已放大时消费 down，pager 不会误翻页；缩放后拖动、双击缩放/还原
 *
 * 手势循环要点（容易踩坑）：
 * - touchSlop 必须按帧累计判断，否则慢速拖动每帧位移都小于 slop，手势永远不会开始；
 * - 手指数量变化（第二根手指落下/抬起）的帧上 calculateZoom/calculatePan 跨指针集计算，
 *   会产生跳变，必须跳过该帧；
 * - pager 一旦消费了事件（1x 单指翻页中），本轮手势整体让给 pager，捏合从下一轮手势开始，
 *   避免与 pager 抢占导致页面卡在半页。
 */
@Composable
internal fun ZoomableImage(
    image: ViewerImage,
    contentDescription: String?,
    contentScale: ContentScale = ContentScale.Fit,
    /** 原图档：非空时叠在最上层，就绪前下层图一直在，不黑屏不闪烁 */
    hdUrl: String? = null,
    /** 详情页图区正在显示的那张：直接画它，开图器零延迟，不依赖缓存命中 */
    preview: Painter? = null,
    /** 量到原图宽高比后回传：滚动模式靠它定每页高度 */
    onIntrinsicAspect: ((Float) -> Unit)? = null,
    onTap: (Int) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var zoom by remember { mutableStateOf(ZoomState()) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    // 原图就绪后撤掉打底的缩略图：静态图留着多占一份内存，动图留着是两张各自逐帧解码。
    // 但要等淡入结束（ImageLoader 配了 200ms crossfade）——淡入没完就撤，底下露出背景色，
    // 观感会从"渐变清晰"变成"闪一下变暗再变清楚"。
    var fullReady by remember(image.fullUrl) { mutableStateOf(false) }
    var thumbnailVisible by remember(image.fullUrl) { mutableStateOf(true) }
    LaunchedEffect(image.fullUrl, fullReady) {
        if (!fullReady) return@LaunchedEffect
        delay(FULL_IMAGE_FADE_MS)
        thumbnailVisible = false
    }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnIntrinsicAspect by rememberUpdatedState(onIntrinsicAspect)

    Box(
        modifier = modifier
            .onSizeChanged { viewport = it }
            .pointerInput(viewport) {
                detectTapGestures(
                    onDoubleTap = { pivot ->
                        val zoomed = zoom.scale > MIN_SCALE + SCALE_EPSILON
                        val target = if (zoomed) MIN_SCALE else DOUBLE_TAP_SCALE
                        zoom = ZoomTransform.zoomTo(zoom, target, pivot, viewport)
                    },
                    onLongPress = { currentOnLongPress() },
                    onTap = { offset -> currentOnTap(tapZone(offset, viewport)) },
                )
            }
            .pointerInput(viewport) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 已放大时立即消费 down，pager 的滑动判定直接取消，水平拖动/慢速拖动不会误翻页
                    if (zoom.scale > MIN_SCALE + SCALE_EPSILON) down.consume()

                    var pastTouchSlop = false
                    var accumulatedZoom = 1f
                    var accumulatedPan = Offset.Zero
                    var lastPointerCount = 1

                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }

                        // pager 已消费（1x 单指翻页中）：本轮手势整体让给 pager
                        if (event.changes.fastAny { it.isConsumed }) continue

                        val zoomed = zoom.scale > MIN_SCALE + SCALE_EPSILON

                        // 1x 单指拖动：不消费，交给 pager 翻页
                        if (!zoomed && pointerCount < 2) continue

                        // 已放大或第二根手指已按下：立即消费，
                        // pager 的触摸判定（会把捏合误判为翻页）看到消费后直接取消
                        event.changes.fastForEach { if (it.positionChanged()) it.consume() }

                        // 手指数量变化的帧上 calculateZoom/calculatePan 跨指针集计算会跳变，跳过
                        val pointerSetChanged = pointerCount != lastPointerCount
                        lastPointerCount = pointerCount
                        if (pointerSetChanged) continue

                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        // 慢速拖动/慢速捏合按帧累计后再判断 touchSlop，
                        // 否则每帧位移都小于 slop，手势永远不会开始
                        if (!pastTouchSlop) {
                            accumulatedZoom *= zoomChange
                            accumulatedPan += panChange
                            val slop = viewConfiguration.touchSlop
                            if (accumulatedPan.getDistance() > slop ||
                                abs(1f - accumulatedZoom) * maxOf(viewport.width, viewport.height) > slop
                            ) {
                                pastTouchSlop = true
                            } else {
                                continue
                            }
                        }

                        zoom = ZoomTransform.transform(
                            current = zoom,
                            zoomChange = zoomChange,
                            panChange = panChange,
                            centroid = event.calculateCentroid(),
                            viewport = viewport,
                        )
                    } while (event.changes.fastAny { it.pressed })
                }
            }
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.offset.x
                translationY = zoom.offset.y
            },
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnailVisible) {
            // 详情页刚画过的那张优先：painter 已含解码好的图，首帧就是内容，等不到网络也等不到解码
            if (preview != null) {
                Image(
                    painter = preview,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    colorFilter = PikuColors.tameWhiteFilter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                AsyncImage(
                    model = rememberAnimatedImage(image.thumbnailUrl),
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    colorFilter = PikuColors.tameWhiteFilter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (image.fullUrl != null && image.fullUrl != image.thumbnailUrl) {
            SubcomposeAsyncImage(
                model = rememberAnimatedImage(image.fullUrl),
                contentDescription = contentDescription,
                contentScale = contentScale,
                colorFilter = PikuColors.tameWhiteFilter,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { state ->
                    fullReady = true
                    // 只有原图的比例可信：缩略图可能被裁过，按它定高会在清晰图到货时"长大"一次
                    val intrinsic = state.painter.intrinsicSize
                    if (intrinsic.width.isFinite() && intrinsic.height.isFinite() &&
                        intrinsic.width > 0f && intrinsic.height > 0f
                    ) {
                        currentOnIntrinsicAspect?.invoke(intrinsic.width / intrinsic.height)
                    }
                },
                // 失败时把缩略图放回来：原图可能是在缩略图已经撤掉之后才失败的
                onError = {
                    fullReady = false
                    thumbnailVisible = true
                },
                loading = {},
                error = {},
            )
        }
        if (hdUrl != null && hdUrl != image.fullUrl) {
            SubcomposeAsyncImage(
                model = rememberAnimatedImage(hdUrl),
                contentDescription = contentDescription,
                contentScale = contentScale,
                colorFilter = PikuColors.tameWhiteFilter,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    }
                },
                // 原图拉不动就停在清晰档：下层一直有图，失败无感
                error = {},
            )
        }
    }
}

/**
 * Translated image viewer with the same zoom/pan gestures as [ZoomableImage].
 * Renders an in-memory Bitmap instead of a URL-loaded image.
 */
@Composable
internal fun TranslatedZoomableImage(
    bitmap: android.graphics.Bitmap,
    contentDescription: String?,
    contentScale: ContentScale = ContentScale.Fit,
    onTap: (Int) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var zoom by remember { mutableStateOf(ZoomState()) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    Box(
        modifier = modifier
            .onSizeChanged { viewport = it }
            .pointerInput(viewport) {
                detectTapGestures(
                    onDoubleTap = { pivot ->
                        val zoomed = zoom.scale > MIN_SCALE + SCALE_EPSILON
                        val target = if (zoomed) MIN_SCALE else DOUBLE_TAP_SCALE
                        zoom = ZoomTransform.zoomTo(zoom, target, pivot, viewport)
                    },
                    onLongPress = { currentOnLongPress() },
                    onTap = { offset -> currentOnTap(tapZone(offset, viewport)) },
                )
            }
            .pointerInput(viewport) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (zoom.scale > MIN_SCALE + SCALE_EPSILON) down.consume()

                    var pastTouchSlop = false
                    var accumulatedZoom = 1f
                    var accumulatedPan = Offset.Zero
                    var lastPointerCount = 1

                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        if (event.changes.fastAny { it.isConsumed }) continue

                        val zoomed = zoom.scale > MIN_SCALE + SCALE_EPSILON
                        if (!zoomed && pointerCount < 2) continue

                        event.changes.fastForEach { if (it.positionChanged()) it.consume() }

                        val pointerSetChanged = pointerCount != lastPointerCount
                        lastPointerCount = pointerCount
                        if (pointerSetChanged) continue

                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (!pastTouchSlop) {
                            accumulatedZoom *= zoomChange
                            accumulatedPan += panChange
                            val slop = viewConfiguration.touchSlop
                            if (accumulatedPan.getDistance() > slop ||
                                abs(1f - accumulatedZoom) * maxOf(viewport.width, viewport.height) > slop
                            ) {
                                pastTouchSlop = true
                            } else {
                                continue
                            }
                        }

                        zoom = ZoomTransform.transform(
                            current = zoom,
                            zoomChange = zoomChange,
                            panChange = panChange,
                            centroid = event.calculateCentroid(),
                            viewport = viewport,
                        )
                    } while (event.changes.fastAny { it.pressed })
                }
            }
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.offset.x
                translationY = zoom.offset.y
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            colorFilter = PikuColors.tameWhiteFilter,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
