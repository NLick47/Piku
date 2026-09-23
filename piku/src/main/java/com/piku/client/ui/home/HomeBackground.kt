package com.piku.client.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.piku.client.data.local.SettingsRepository
import kotlin.math.roundToInt

/** 头部区下缘渐隐的色标：宽段留给清晰区，末段交给毛玻璃层接住 */
private const val FADE_START = 0.45f
private const val FADE_END = 1f

/** 编辑模式把渐隐收窄，留出可见的边界线好对齐 */
private const val FADE_START_EDIT = 0.72f
private const val FADE_END_EDIT = 0.96f

/**
 * 自定义首页背景（hero 头部清晰图 + 可分离的毛玻璃 backdrop）。
 * 独立文件避免 HomeScreen 过大；与 BackgroundBackdrop 中的默认渐变背景共用同一包可见性约定。
 *
 * 头部图的取景（[heroFrame]）由调用方按 [contentFrame] 算好传进来：绘制、拖拽手势、
 * 编辑蓝图、标签行取色共用同一份几何。毛玻璃层仍是"铺满 + Crop 对齐偏置"的老结构——
 * 它整屏铺底，改成显式矩形会把模糊层放大到超出屏幕，白付 GPU 开销。
 *
 * 视差位移只在绘制阶段读（[scrolledOverTopPx] 一律在 graphicsLayer / drawBehind 里调用），
 * 滚动不会触发重组；头部图、遮罩、毛玻璃共用同一个位移源，三层不会各自漂。
 */
@Composable
internal fun CustomHomeBackground(
    heroPath: String,
    /** 头部图绘制矩形；null（缺图片尺寸的老数据）退回居中裁切、不可拖拽取景 */
    heroFrame: ContentFrame?,
    /** 头部清晰区高度 */
    heroHeight: Dp,
    heroScale: Float,
    /** 毛玻璃层图片：null 表示跟随头部图 */
    backdropPath: String?,
    frostScale: Float,
    frostOffsetX: Float,
    frostOffsetY: Float,
    dim: Float,
    dark: Boolean,
    scrimDark: Int?,
    scrimLight: Int?,
    blurDp: Float = SettingsRepository.BACKGROUND_BLUR_DEFAULT,
    editMode: Boolean = false,
    scrolledOverTopPx: () -> Int = { 0 },
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val heroHeightPx = with(density) { heroHeight.toPx() }
    // 编辑模式冻结视差：所见即所调就是停顶状态
    val heroShift = { if (editMode) 0f else heroParallaxOffsetPx(scrolledOverTopPx()) }
    val framedT = ((1f - heroScale) / (1f - SettingsRepository.HERO_SCALE_MIN)).coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(java.io.File(backdropPath ?: heroPath))
                .size(coil3.size.Size(128, 128))
                .build(),
            contentDescription = null,
            alignment = BiasAlignment(frostOffsetX, frostOffsetY),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 位移必须在 blur 之前（blur 在里的效果等价于先平移再模糊）：
                    // 放在 blur 后面，模糊的 clamp 边缘带会随位移进出视野，视觉上抽搐。
                    // 毛玻璃层作为环境光只随滚动极缓上漂，比头部慢
                    translationY = -frostShiftPx(heroShift())
                    scaleX = frostScale
                    scaleY = frostScale
                }
                .blur(blurDp.dp),
        )

        val zone = Modifier.fillMaxWidth().height(heroHeight)
        val framedFrame = heroFrame?.takeIf { it.framed }
        if (framedFrame != null) {
            // 画框式（缩放 <1）：整幅落在头部区内，留白处露出毛玻璃层，投影可以漫出头部区
            val cardW = with(density) { framedFrame.width.toDp() }
            val cardH = with(density) { framedFrame.height.toDp() }
            val corner = lerp(0.dp, 24.dp, framedT)
            Box(zone) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(java.io.File(heroPath)).build(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .graphicsLayer { translationY = -heroShift() }
                        .offset {
                            IntOffset(framedFrame.left.roundToInt(), framedFrame.top.roundToInt())
                        }
                        // requiredSize：图片尺寸由取景算出，可能比头部区大，不能被父约束夹住（夹住就会压扁）
                        .requiredSize(cardW, cardH)
                        .shadow(elevation = 14.dp * framedT, shape = RoundedCornerShape(corner))
                        .clip(RoundedCornerShape(corner)),
                )
            }
        } else {
            // 裁切取景（缩放 ≥1）：图片挂出头部区，下缘渐隐交给毛玻璃层接住。
            // 裁剪与渐隐都锚在头部区上（不是图上）：缩放/平移时清晰区边界不动，
            // 与编辑蓝图画的边界线一致，图片也不会溢出到列表后面。
            Box(
                zone
                    .graphicsLayer {
                        clip = true
                        compositingStrategy = CompositingStrategy.Offscreen
                        translationY = -heroShift()
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black,
                                (if (editMode) FADE_START_EDIT else FADE_START) to Color.Black,
                                (if (editMode) FADE_END_EDIT else FADE_END) to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                if (heroFrame != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(java.io.File(heroPath)).build(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .offset {
                                IntOffset(heroFrame.left.roundToInt(), heroFrame.top.roundToInt())
                            }
                            // requiredSize：裁切取景时图片本来就比头部区大，不能被父约束夹住
                            .requiredSize(
                                with(density) { heroFrame.width.toDp() },
                                with(density) { heroFrame.height.toDp() },
                            ),
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(java.io.File(heroPath)).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = heroScale
                                scaleY = heroScale
                            },
                    )
                }
            }
        }

        val scrim = veilColor(dark, scrimDark, scrimLight)
        val dimBase = veilDim(dark, dim)
        val midFactor = veilMidFactor(dark)
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    // 压暗遮罩与头部图同步上移：图动遮罩不动，暗部会浮在图上。
                    // 上移 shift 后下缘在 heroHeight - shift，渐变亮带跟着收窄。
                    // 色标由 veilStops 给出，与标签行取色的取样口径同源。
                    val heroPx = (heroHeightPx - heroShift()).coerceAtLeast(0f)
                    val heroFrac = (heroPx / size.height).coerceIn(0f, 0.9f)
                    val stops = veilStops(heroFrac, dimBase, midFactor)
                    drawRect(
                        brush = Brush.verticalGradient(
                            *stops
                                .map { (at, alpha) -> at to scrim.copy(alpha = alpha) }
                                .toTypedArray(),
                        ),
                    )
                },
        )
    }
}
