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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.progressSemantics
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkSource
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.PikuLayout
import com.piku.client.ui.theme.RankBronze
import com.piku.client.ui.theme.RankBronzeInk
import com.piku.client.ui.theme.RankBronzeBorder
import com.piku.client.ui.theme.RankGold
import com.piku.client.ui.theme.RankGoldBorder
import com.piku.client.ui.theme.RankGoldInk
import com.piku.client.ui.theme.RankRingOutline
import com.piku.client.ui.theme.RankSilver
import com.piku.client.ui.theme.RankSilverBorder
import com.piku.client.ui.theme.RankSilverInk
import com.piku.client.ui.theme.SoftBorderLight
import com.piku.client.ui.theme.WorkCardBgDark
import com.piku.client.ui.theme.WorkCardBorderDark
import com.piku.client.ui.navigation.sharedWorkBounds
import com.piku.client.ui.navigation.workSharedKey
import com.piku.client.ui.theme.WorkCardPlaceholderDark
import kotlinx.coroutines.delay

internal fun feedThumbUrl(url: String): String =
    if ("_640.jpg" in url) url.replace("_640.jpg", "_360.jpg") else url

/**
 * 通栏大卡位（榜单 rank 1）的图档：榜单接口给的是 `c/480x960` 裁切档，铺满通栏会糊一档，
 * 换成同文件的 master1200——详情页首图同款，共享转场直接命中缓存，且通栏只有 rank 1
 * 一张卡承担大图开销。
 */
internal fun heroFeedThumbUrl(url: String): String =
    if (url.startsWith("https://i.pximg.net/c/") && url.contains("/img-master/")) {
        "https://i.pximg.net/" + url.removePrefix("https://i.pximg.net/c/").substringAfter('/')
    } else {
        url
    }

/**
 * 卡片上的阅读进度条。[fraction] 为 0~1 的完成比例，[label] 形如 "42%" 或 "5/12"。
 * 由调用方（收藏夹）算出后传入，WorkCard 只负责画。
 */
data class CardProgress(val fraction: Float, val label: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkCard(
    work: Work,
    isFavorite: Boolean,
    onToggleFavorite: (Work) -> Unit,
    onClick: (Work) -> Unit,
    dark: Boolean,
    onLongClick: ((Work) -> Unit)? = null,
    /** 作者区（头像 + 昵称）点击：进该作者作品页。null 表示作者区不可点，整卡仍进详情 */
    onAuthorClick: ((Work) -> Unit)? = null,
    /** 阅读进度；null 表示不显示（首页/搜索等没有"读到哪"语义的场景） */
    progress: CardProgress? = null,
    /** 跨源列表（收藏/历史）里标出源；poipiku 是默认源，不标注 */
    showSourceLabel: Boolean = false,
    /** 名次（榜单流）：非空时在缩略图右上角挂名次角标 */
    rank: Int? = null,
) {
    val shape = RoundedCornerShape(PikuLayout.CardCorner)
    // 前三名压在名次色描边上：奖章只说得出"这张是第几名"，描边才把前三名整组从瀑布流里拎出来
    val rankBorder = rank?.let { rankAccent(it) }
    var heartVisible by remember { mutableStateOf(false) }
    val authorInteraction = remember { MutableInteractionSource() }
    val authorPressed by authorInteraction.collectIsPressedAsState()

    Column(
        modifier = Modifier
            .shadow(
                elevation = if (dark) 0.dp else 6.dp,
                shape = shape,
                ambientColor = Color(0x1F000000),
                spotColor = Color(0x33000000),
            )
            .clip(shape)
            .background(if (dark) WorkCardBgDark else Color(0xE6FFFFFF))
            .border(
                BorderStroke(
                    if (rankBorder != null) 1.dp else 0.5.dp,
                    rankBorder ?: if (dark) WorkCardBorderDark else SoftBorderLight,
                ),
                shape,
            )
            .combinedClickable(
                // 回调直接透传 work：调用方无需在 item lambda 里包装闭包，
                // 参数稳定时 Compose 可跳过未变化卡片的重组
                onClick = { onClick(work) },
                onLongClick = onLongClick?.let { handler -> { handler(work) } },
                // 双击收藏：心形爆裂给即时反馈，否则整张卡看不出刚才那下有没有生效
                onDoubleClick = {
                    heartVisible = true
                    onToggleFavorite(work)
                },
            ),
    ) {
        Box(Modifier.fillMaxWidth()) {
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
                        tint = PikuColors.textFaint,
                        modifier = Modifier.size(32.dp),
                    )
                }
            } else {
                AsyncImage(
                    model = feedThumbUrl(work.thumbnailUrl),
                    contentDescription = work.title,
                    // 暗色模式下压暗白底缩略图，避免网格里出现刺眼的"亮块"
                    colorFilter = PikuColors.tameWhiteFilter,
                    modifier = Modifier
                        .sharedWorkBounds(workSharedKey(work.authorId, work.id))
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(if (dark) WorkCardPlaceholderDark else Color(0xFFF1EFEA)),
                    contentScale = ContentScale.Crop,
                )
            }
            // 名次和内容角标合成左上角一列：分挂两个角的话，一张卡就有两个"先看这里"。
            // 名次排最前——它是榜单页的主语，AI/私密/动图只是限定条件。
            // （动图角标：网格不播放动画，几十张同时逐帧解码会拖垮滚动，只标出这是动图）
            if (rank != null || work.isPrivate || work.ai || isAnimatedImage(work.thumbnailUrl)) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (rank != null) {
                        // 前三名是奖章（金/银/铜），4 名起换半透明小片：每张卡都顶白底圆点太吵
                        if (rank <= RANK_MEDAL_MAX) {
                            RankMedal(rank = rank, large = false)
                        } else {
                            RankChip(rank = rank)
                        }
                    }
                    if (work.isPrivate) {
                        WorkPrivateBadge()
                    }
                    if (isAnimatedImage(work.thumbnailUrl)) {
                        Text(
                            // 格式名，各语言写法一致，不走 i18n
                            text = "GIF",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x99000000))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                    if (work.ai) {
                        WorkAiBadge()
                    }
                }
            }
            if (work.imageCount > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_image_count, work.imageCount),
                        color = Color.White,
                        fontSize = 10.sp,
                    )
                }
            }
            FavoriteHeartBurst(
                visible = heartVisible,
                onFinished = { heartVisible = false },
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = PikuLayout.CardPadding,
                    end = PikuLayout.CardPadding,
                    top = 10.dp,
                    bottom = if (onAuthorClick != null) 0.dp else 12.dp,
                ),
        ) {
            Text(
                text = work.title,
                color = PikuColors.textPrimary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (progress != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (dark) Color(0x33FFFFFF) else Color(0x1A000000))
                            // 没有语义时读屏只会念旁边的 "5/12"，念不出这是个进度
                            .progressSemantics(progress.fraction),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.fraction.coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .background(PikuColors.accent),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = progress.label,
                        color = PikuColors.textFaint,
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }
            }
            // 热区自带 8dp 上下内距，标题与头像之间留 0dp 即可（实际 = 8dp）；不可点时维持 6dp
            Spacer(Modifier.height(if (onAuthorClick != null) 0.dp else 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (onAuthorClick != null) {
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(
                                        interactionSource = authorInteraction,
                                        indication = LocalIndication.current,
                                        onClick = { onAuthorClick(work) },
                                    )
                                    .padding(vertical = 8.dp)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    AsyncImage(
                        model = work.authorAvatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = work.authorName,
                        color = if (authorPressed) PikuColors.accent else PikuColors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (work.categoryName.isNotBlank()) {
                    Text(
                        text = localizedCategoryName(work.categoryCd, work.categoryName),
                        color = PikuColors.textFaint,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                } else if (showSourceLabel && work.source != WorkSource.POIPIKU) {
                    Text(
                        text = stringResource(work.source.labelRes()),
                        color = PikuColors.textFaint,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}


/**
 * AI 生成角标：pixiv 官方申报口径，部分 AI 加工不标。官方缩略图位也是左上角，
 * 文案各语言写法一致，不走 i18n
 */
@Composable
fun WorkAiBadge(modifier: Modifier = Modifier) {
    Text(
        text = "AI",
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x99000000))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
fun WorkPrivateBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x99000000))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(10.dp),
        )
        Text(
            text = stringResource(R.string.work_private_badge),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
fun LoaderDots(dark: Boolean) {
    val transition = rememberInfiniteTransition(label = "loader")
    val dotColor = PikuColors.textSecondary
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

/** 双击收藏的心形爆裂：[WorkCard]、[ProportionalWorkCard] 与榜单聚光卡共用 */
@Composable
internal fun FavoriteHeartBurst(
    visible: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val heartScale = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            heartScale.snapTo(0f)
            heartScale.animateTo(1.3f, tween(120, easing = LinearOutSlowInEasing))
            heartScale.animateTo(1f, tween(80, easing = LinearOutSlowInEasing))
            delay(180)
            onFinished()
        }
    }
    if (!visible) return
    Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        tint = PikuColors.accent,
        modifier = modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = heartScale.value
                scaleY = heartScale.value
            },
    )
}

/** 前三名名次奖章：金银铜圆形。榜单聚光卡（[large]）与网格卡的 2、3 名共用 */
@Composable
internal fun RankMedal(
    rank: Int,
    large: Boolean,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = when (rank) {
        1 -> RankGold to RankGoldInk
        2 -> RankSilver to RankSilverInk
        3 -> RankBronze to RankBronzeInk
        else -> Color(0xE6FFFFFF) to Color(0xFF3A3632)
    }
    val size = if (large) 32.dp else 22.dp
    val rankLabel = rememberRankLabel(rank)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            // 图底千变万化：白环当高光把奖章从图里拎起来，外面再兜一圈极淡暗描兜底
            .border(1.5.dp, Color.White, CircleShape)
            .border(0.5.dp, RankRingOutline, CircleShape)
            .semantics { contentDescription = rankLabel },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.toString(),
            color = fg,
            fontSize = if (large) 15.sp else 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 前三名的名次色（卡片描边用）；4 名起没有 */
internal fun rankAccent(rank: Int): Color? = when (rank) {
    1 -> RankGoldBorder
    2 -> RankSilverBorder
    3 -> RankBronzeBorder
    else -> null
}

/** 4 名起的名次小片：与 GIF/AI 同款的半透明圆角片，不抢画面 */
@Composable
internal fun RankChip(rank: Int, modifier: Modifier = Modifier) {
    val rankLabel = rememberRankLabel(rank)
    Text(
        text = rank.toString(),
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0x99000000))
            .padding(horizontal = 7.dp, vertical = 3.dp)
            .semantics { contentDescription = rankLabel },
    )
}

/** 前三名才用奖章；4 名起换小片，避免满屏同款圆点 */
internal const val RANK_MEDAL_MAX = 3

/** 名次的读屏文案（"第 4 名"）：角标本身只是个数字，不拼语义的话屏幕阅读器只念得出数字 */
@Composable
private fun rememberRankLabel(rank: Int): String {
    val pattern = stringResource(R.string.ranking_rank_position)
    return remember(pattern, rank) { String.format(pattern, rank) }
}
