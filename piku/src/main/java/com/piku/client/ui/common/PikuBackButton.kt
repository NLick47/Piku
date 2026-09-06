package com.piku.client.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.piku.client.R
import com.piku.client.ui.theme.ChevronLeftIcon
import com.piku.client.ui.theme.GlassIconBgDark
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.ShadowAmbient
import com.piku.client.ui.theme.ShadowSpot
import com.piku.client.ui.theme.SoftBorderDark
import com.piku.client.ui.theme.SoftBorderLight

/** 统一返回按钮：细长左尖括号。默认纯图标无背景，仅登录/注册等独立浮层场景用 glass 圆形磨砂底 */
@Composable
fun PikuBackButton(
    onClick: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    glass: Boolean = false,
    /** 阅读器等自带配色的场景可覆盖图标色，默认跟随主题文字色 */
    tint: Color? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "backButtonScale",
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val icon: @Composable () -> Unit = {
            Icon(
                imageVector = ChevronLeftIcon,
                contentDescription = contentDescription ?: stringResource(R.string.back),
                tint = tint ?: PikuColors.textPrimary,
                modifier = Modifier.size(if (glass) 22.dp else 26.dp),
            )
        }
        if (glass) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(6.dp, CircleShape, ambientColor = ShadowAmbient, spotColor = ShadowSpot)
                    .clip(CircleShape)
                    .background(if (dark) GlassIconBgDark else PikuColors.surface)
                    .border(
                        BorderStroke(0.5.dp, if (dark) SoftBorderDark else SoftBorderLight),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
        } else {
            icon()
        }
    }
}
