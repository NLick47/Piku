package com.piku.client.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import kotlinx.coroutines.delay

/** 展开后文字停留的默认时间；功能引导这类需要更久驻留的场景可传入更长值 */
const val DEFAULT_EXPAND_MILLIS = 3_000L
/** 文字与图标的间距 */
private val LABEL_GAP_DP = 6.dp

/**
 * 「图标 + 可展开文字」的单一可点击控件：文字在图标左侧，二者共享同一份点击热区与圆角背景，
 * 外观上是一个整体。
 *
 * - 默认只显示图标；展开时向左伸出文字，[expandDurationMillis] 后自动收起，只留图标。
 * - 展开期间再次点击会重置计时；**每次点击都触发一次 [onClick]**——先执行业务逻辑再处理
 *   展开/重置，所以点击既不会被吞掉，也不会因为展开而重复调用。
 * - [autoExpand] 用于首次进入的功能引导：进入组合即自动展开一次，不必等用户先点。
 * - 容器高度恒定；收起时文字宽度压到 0，是真正移除占位而非留一块透明区域；
 *   展开时左边缘向左伸展、右边缘固定，图标位置保持稳定。
 * - 计时跑在 `LaunchedEffect` 里，组件卸载或 key 变化时旧协程自动取消，不会残留或多重计时。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandableIconAction(
    label: String,
    dark: Boolean,
    onClick: () -> Unit,
    /** 图标区（调用方也可以放转圈等自定义内容） */
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    /** 收起态容器底色：压在图片上的按钮需要常驻半透明底，否则在亮图上会看不见 */
    containerColor: Color = Color.Transparent,
    /** 展开态容器底色；传 null 表示沿用 [containerColor]，只用缩放和文字做反馈 */
    expandedContainerColor: Color? = null,
    enabled: Boolean = true,
    /** 进入组合后自动展开一次 */
    autoExpand: Boolean = false,
    /** 展开后文字停留多久；功能引导一般比点击反馈停留更久 */
    expandDurationMillis: Long = DEFAULT_EXPAND_MILLIS,
    /**
     * [autoExpand] 真的把文字展开出来时回调一次。
     * 外部借此知道「这次引导确实被看到了」，用于消耗一次性标记——
     * 比在页面初始化时就消耗精确：按钮没出现（例如没配对应模型）就不会白白用掉机会。
     */
    onAutoExpandShown: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    height: Dp = 40.dp,
    horizontalPadding: Dp = 10.dp,
) {
    val reducedMotion = rememberReducedMotion()

    // 展开态与计时重置：expanded 已经是 true 时再点一次不会重启 LaunchedEffect，
    // 所以额外用 resetToken 强制重启，实现「计时内再次点击重新开始计时」
    var expanded by remember { mutableStateOf(false) }
    var resetToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(expanded, resetToken, expandDurationMillis) {
        if (!expanded) return@LaunchedEffect
        delay(expandDurationMillis)
        expanded = false
    }
    val currentOnAutoExpandShown by rememberUpdatedState(onAutoExpandShown)
    // 用 autoExpand 当 key：按钮可能比页面晚出现（例如图片加载完才显示），
    // 那时 autoExpand 才变为 true，仍然要能触发展开
    LaunchedEffect(autoExpand) {
        if (autoExpand) {
            expanded = true
            currentOnAutoExpandShown?.invoke()
        }
    }

    // 文字宽度参与动画，收起时压到 0 —— 不是留一块透明区域，而是真正没有占位
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textWidthDp = remember(label, density) {
        val widthPx = textMeasurer.measure(
            AnnotatedString(label),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        ).size.width
        with(density) { widthPx.toDp() }
    }
    val textWidth by animateDpAsState(
        targetValue = if (expanded) textWidthDp + LABEL_GAP_DP else 0.dp,
        animationSpec = tween(durationMillis = motionDuration(reducedMotion, 220)),
        label = "expandableActionTextWidth",
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = motionDuration(reducedMotion, 180)),
        label = "expandableActionTextAlpha",
    )

    // 展开瞬间的放大回弹，与底栏收藏星标用的是同一套 Animatable + spring 方案
    val scale = remember { Animatable(1f) }
    LaunchedEffect(expanded, resetToken) {
        if (!expanded || reducedMotion) return@LaunchedEffect
        scale.snapTo(1f)
        scale.animateTo(1.08f, tween(110))
        scale.animateTo(
            1f,
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    val resolvedColor = if (expanded && expandedContainerColor != null) {
        expandedContainerColor
    } else {
        containerColor
    }
    val pill = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(pill)
            .background(resolvedColor)
            .combinedClickable(
                enabled = enabled,
                onClick = {
                    onClick()
                    if (expanded) resetToken++ else expanded = true
                },
                onLongClick = onLongClick,
            )
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 刻意不用 Arrangement.spacedBy：收起时文字宽度为 0，固定间距会把图标推离中心
            Text(
                text = label,
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .width(textWidth)
                    .padding(end = LABEL_GAP_DP)
                    .graphicsLayer { alpha = textAlpha },
            )
            icon()
        }
    }
}
