package com.piku.client.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.domain.model.Work
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.LoginCardDark
import com.piku.client.ui.theme.LoginTextFaintDark
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.LoginTextSecondaryLight
import com.piku.client.ui.theme.TameWhiteColorFilter
import com.piku.client.ui.theme.WorkCardBgDark
import com.piku.client.ui.theme.WorkCardBorderDark
import com.piku.client.ui.theme.WorkCardInfoBgDark
import com.piku.client.ui.theme.WorkCardPlaceholderDark
import kotlinx.coroutines.delay

internal fun feedThumbUrl(url: String): String =
    if ("_640.jpg" in url) url.replace("_640.jpg", "_360.jpg") else url

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkCard(
    work: Work,
    isFavorite: Boolean,
    onToggleFavorite: (Work) -> Unit,
    onClick: (Work) -> Unit,
    dark: Boolean,
    onLongClick: ((Work) -> Unit)? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    var heartVisible by remember { mutableStateOf(false) }
    val heartScale = remember { Animatable(0f) }

    LaunchedEffect(heartVisible) {
        if (heartVisible) {
            heartScale.snapTo(0f)
            heartScale.animateTo(1.3f, tween(120, easing = LinearOutSlowInEasing))
            heartScale.animateTo(1f, tween(80, easing = LinearOutSlowInEasing))
            delay(180)
            heartVisible = false
        }
    }

    Column(
        modifier = Modifier
            .shadow(
                elevation = if (dark) 6.dp else 10.dp,
                shape = shape,
                ambientColor = Color(0x33000000),
                spotColor = Color(0x40000000),
            )
            .clip(shape)
            .background(if (dark) WorkCardBgDark else Color(0xCCFFFFFF))
            .border(
                BorderStroke(
                    1.dp,
                    if (dark) WorkCardBorderDark else Color(0x59C8C2B8),
                ),
                shape,
            )
            .combinedClickable(
                // 回调直接透传 work：调用方无需在 item lambda 里包装闭包，
                // 参数稳定时 Compose 可跳过未变化卡片的重组
                onClick = { onClick(work) },
                onLongClick = onLongClick?.let { handler -> { handler(work) } },
                onDoubleClick = { onToggleFavorite(work) },
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            if (work.thumbnailUrl.isBlank()) {
                // 兜底：历史/收藏里无缩略图信息的旧记录，显示中性占位而非空白
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(if (dark) WorkCardPlaceholderDark else Color(0xFFF1EFEA)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = work.title,
                        tint = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                        modifier = Modifier.size(32.dp),
                    )
                }
            } else {
                AsyncImage(
                    model = feedThumbUrl(work.thumbnailUrl),
                    contentDescription = work.title,
                    // 暗色模式下压暗白底缩略图，避免网格里出现刺眼的"亮块"
                    colorFilter = if (dark) TameWhiteColorFilter else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(if (dark) WorkCardPlaceholderDark else Color(0xFFF1EFEA)),
                    contentScale = ContentScale.Crop,
                )
            }
            if (work.imageCount > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_image_count, work.imageCount),
                        color = Color.White,
                        fontSize = 9.sp,
                    )
                }
            }
            if (heartVisible) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = if (dark) LoginTextPrimaryDark else AccentDark,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .graphicsLayer {
                            scaleX = heartScale.value
                            scaleY = heartScale.value
                        },
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(if (dark) WorkCardInfoBgDark else Color(0xF2FFFFFF))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = work.title,
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = work.authorAvatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = work.authorName,
                    color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (work.categoryName.isNotBlank()) {
                    Text(
                        text = work.categoryName,
                        color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun LoaderDots(dark: Boolean) {
    val transition = rememberInfiniteTransition(label = "loader")
    val dotColor = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = index * 180),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .size(5.dp)
                    .graphicsLayer {
                        this.alpha = alpha
                        scaleX = 0.7f + alpha * 0.3f
                        scaleY = 0.7f + alpha * 0.3f
                    }
                    .background(dotColor, CircleShape),
            )
        }
    }
}