package com.piku.client.ui.source

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.domain.model.AppLanguage
import com.piku.client.ui.theme.GlassBarBgDark
import com.piku.client.ui.theme.GlassBarBgLight
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.ShadowAmbient
import com.piku.client.ui.theme.ShadowSpot
import com.piku.client.ui.theme.SoftBorderDark
import com.piku.client.ui.theme.SoftBorderLight
import com.piku.client.ui.theme.StarDark
import com.piku.client.ui.theme.StarLight
import com.piku.client.ui.theme.StarTintDark
import com.piku.client.ui.theme.StarTintLight

/** 个位数的收藏也撑成同一块头，不会挤成一颗扁丸子 */
private val BOOKMARK_PILL_MIN_WIDTH = 60.dp

/**
 * 收藏开关：星标 + 收藏数的玻璃胶囊，插画详情与小说详情共用这一颗。
 * 点亮=金色实心（收藏后描边与投影也换星标色），长按进收藏夹面板，已同步 pixiv 时带角标。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookmarkStatButton(
    count: Int,
    language: AppLanguage,
    favorited: Boolean,
    cloudSynced: Boolean,
    dark: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val star = if (dark) StarDark else StarLight
    // 星标弹跳，与详情底栏的收藏星同款
    val starScale = remember { Animatable(1f) }
    LaunchedEffect(favorited) {
        if (favorited) {
            starScale.snapTo(1.35f)
            starScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .defaultMinSize(minWidth = BOOKMARK_PILL_MIN_WIDTH)
            .shadow(
                4.dp,
                shape,
                ambientColor = ShadowAmbient,
                spotColor = if (favorited) star.copy(alpha = 0.45f) else ShadowSpot,
            )
            .clip(shape)
            // 玻璃底与关注胶囊同族：未收藏白霜玻璃，收藏后换淡金玻璃
            .background(
                if (favorited) if (dark) StarTintDark else StarTintLight
                else if (dark) GlassBarBgDark else GlassBarBgLight,
            )
            // 收藏态用星标色自己描边：淡金底配金边才是颗完整的金色胶囊，中性软边会显得发灰
            .border(
                BorderStroke(
                    0.5.dp,
                    if (favorited) star.copy(alpha = 0.5f) else if (dark) SoftBorderDark else SoftBorderLight,
                ),
                shape,
            )
            .combinedClickable(onClick = onToggle, onLongClick = onLongPress)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Box {
            Icon(
                imageVector = if (favorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = stringResource(R.string.detail_favorite),
                tint = if (favorited) star else PikuColors.textSecondary,
                modifier = Modifier
                    .size(15.dp)
                    .graphicsLayer {
                        scaleX = starScale.value
                        scaleY = starScale.value
                    },
            )
            // 同步角标：只表示「本 App 已把这条收藏送上 pixiv」
            if (favorited && cloudSynced) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(6.dp)
                        .background(PikuColors.controlAccent, CircleShape)
                        .border(1.dp, PikuColors.surface, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = compactCount(count, language),
            color = PikuColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
