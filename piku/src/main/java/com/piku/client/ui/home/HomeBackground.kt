package com.piku.client.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.piku.client.data.local.SettingsRepository
import com.piku.client.ui.theme.HomeBgTopDark
import kotlin.math.roundToInt

/**
 * 自定义首页背景（hero 头部清晰图 + 可分离的毛玻璃 backdrop）。
 * 独立文件避免 HomeScreen 过大；与 BackgroundBackdrop 中的默认渐变背景共用同一包可见性约定。
 */
@Composable
internal fun CustomHomeBackground(
    heroPath: String,
    backdropPath: String?,
    dim: Float,
    backdropScale: Float,
    dark: Boolean,
    scrimDark: Int?,
    scrimLight: Int?,
    heroOffsetX: Float = 0f,
    heroOffsetY: Float = 0f,
    heroScale: Float = SettingsRepository.HERO_SCALE_DEFAULT,
    backdropOffsetX: Float = 0f,
    backdropOffsetY: Float = 0f,
    imgWidth: Int? = null,
    imgHeight: Int? = null,
    blurDp: Float = SettingsRepository.BACKGROUND_BLUR_DEFAULT,
    heroFraction: Float = SettingsRepository.BACKGROUND_HERO_DEFAULT,
    editMode: Boolean = false,
) {
    val context = LocalContext.current
    val heroHeight = (LocalConfiguration.current.screenHeightDp.dp * heroFraction)
        .coerceIn(200.dp, 420.dp)
    val heroAlignment = BiasAlignment(heroOffsetX, heroOffsetY)
    val separated = backdropPath != null

    Box(Modifier.fillMaxSize()) {
        val frostScale = if (separated) backdropScale else heroScale.coerceAtLeast(1f)
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(java.io.File(backdropPath ?: heroPath))
                .size(coil3.size.Size(128, 128))
                .build(),
            contentDescription = null,
            alignment = if (separated) {
                BiasAlignment(backdropOffsetX, backdropOffsetY)
            } else {
                heroAlignment
            },
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(blurDp.dp)
                .graphicsLayer {
                    scaleX = frostScale
                    scaleY = frostScale
                }
        )

        val heroImgW = imgWidth?.takeIf { it > 0 }
        val heroImgH = imgHeight?.takeIf { it > 0 }
        val framedT = if (heroImgW != null && heroImgH != null) {
            ((1f - heroScale) / (1f - SettingsRepository.HERO_SCALE_MIN)).coerceIn(0f, 1f)
        } else {
            0f
        }
        if (framedT > 0f && heroImgW != null && heroImgH != null) {
            BoxWithConstraints(
                Modifier.fillMaxWidth().height(heroHeight),
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current
                val zoneWpx = with(density) { maxWidth.toPx() }
                val zoneHpx = with(density) { maxHeight.toPx() }
                val coverFactor = maxOf(zoneWpx / heroImgW, zoneHpx / heroImgH)
                val cardWpx = heroImgW * coverFactor * heroScale
                val cardHpx = heroImgH * coverFactor * heroScale
                val cardW = with(density) { cardWpx.toDp() }
                val cardH = with(density) { cardHpx.toDp() }
                val slackX = ((zoneWpx - cardWpx) / 2f).coerceAtLeast(0f)
                val slackY = ((zoneHpx - cardHpx) / 2f).coerceAtLeast(0f)
                val corner = lerp(0.dp, 24.dp, framedT)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(java.io.File(heroPath))
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (heroOffsetX * slackX).roundToInt(),
                                (heroOffsetY * slackY).roundToInt(),
                            )
                        }
                        .size(cardW, cardH)
                        .shadow(elevation = 14.dp * framedT, shape = RoundedCornerShape(corner))
                        .clip(RoundedCornerShape(corner)),
                )
            }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(java.io.File(heroPath))
                    .build(),
                contentDescription = null,
                alignment = heroAlignment,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .graphicsLayer {
                        scaleX = heroScale
                        scaleY = heroScale
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black,
                                (if (editMode) 0.72f else 0.45f) to Color.Black,
                                (if (editMode) 0.96f else 1f) to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
            )
        }

        val darkTint = (scrimDark?.let { Color(it) } ?: Color.Black)
            .blendInto(HomeBgTopDark, DARK_TINT_BLEND)
        val scrim = if (dark) darkTint else scrimLight?.let { Color(it) } ?: Color.White
        val dimBase = if (dark) (dim + DARK_DIM_EXTRA).coerceAtMost(DARK_DIM_MAX) else dim
        val midFactor = if (dark) DARK_DIM_MID_FACTOR else 0.15f
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val heroFrac = (heroHeight.toPx() / size.height).coerceIn(0f, 0.9f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to scrim.copy(alpha = dimBase),
                            (heroFrac * 0.18f) to scrim.copy(alpha = dimBase * midFactor),
                            (heroFrac + 0.04f).coerceAtMost(1f) to scrim.copy(alpha = dimBase),
                            1f to scrim.copy(alpha = dimBase * 0.6f),
                        ),
                    )
                },
        )
    }
}
