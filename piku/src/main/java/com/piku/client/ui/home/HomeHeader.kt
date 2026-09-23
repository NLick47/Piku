package com.piku.client.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.domain.model.PoipikuCategory
import com.piku.client.ui.common.UserAvatar
import com.piku.client.ui.theme.GlassIconBgDark
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.PikuColors
import com.piku.client.ui.theme.PikuLayout

@Composable
internal fun GlassHeader(
    state: HomeUiState,
    avatarUrl: String?,
    onMenuClick: () -> Unit,
    menuEnabled: Boolean,
    onSearchClick: () -> Unit,
    onSelectFeedTab: (FeedTab) -> Unit,
    onCategoryClick: () -> Unit,
    onDoubleTapTop: () -> Unit,
    dark: Boolean,
    isScrolling: State<Boolean>,
    scrollProgress: () -> Float,
    drawerIsOpen: Boolean = false,
    /** 列表停在顶部：自定义背景下头部底衬整体退场，把清晰头图让出来 */
    atTop: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(onDoubleTapTop) {
                detectTapGestures(onDoubleTap = { onDoubleTapTop() })
            }
            .statusBarsPadding()
            .padding(top = GlassHeaderTopPadding),
    ) {
        LiquidGlassBackdrop(
            dark = dark,
            isScrolling = isScrolling,
            drawerIsOpen = drawerIsOpen,
            modifier = Modifier.matchParentSize(),
            translucent = state.customBackgroundPath != null,
            progress = scrollProgress,
            atTop = atTop,
        )
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PikuLayout.NavRowInset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserMenuButton(
                    avatarUrl = avatarUrl,
                    onMenuClick = onMenuClick,
                    enabled = menuEnabled,
                    dark = dark,
                )
                Spacer(Modifier.weight(1f))
                SearchMenuButton(
                    onClick = onSearchClick,
                    dark = dark,
                )
            }
            FeedTabRow(
                feedTab = state.feedTab,
                onSelectFeedTab = onSelectFeedTab,
                dark = dark,
                // 分类只作用于「最新」源，其余源隐藏；它排在标签行右端，不与顶行控件抢头图
                categoryLabel = if (state.feedTab == FeedTab.LATEST) {
                    stringResource(state.category.nameRes)
                } else {
                    null
                },
                categoryActive = state.category != PoipikuCategory.ALL,
                onCategoryClick = onCategoryClick,
            )
        }
    }
}

@Composable
internal fun TabletTopBar(
    avatarUrl: String?,
    onMenuClick: () -> Unit,
    menuEnabled: Boolean,
    onSearchClick: () -> Unit,
    onDoubleTapTop: () -> Unit,
    dark: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(onDoubleTapTop) {
                detectTapGestures(onDoubleTap = { onDoubleTapTop() })
            }
            .padding(horizontal = PikuLayout.NavRowInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserMenuButton(
            avatarUrl = avatarUrl,
            onMenuClick = onMenuClick,
            enabled = menuEnabled,
            dark = dark,
        )
        Spacer(Modifier.weight(1f))
        SearchMenuButton(
            onClick = onSearchClick,
            dark = dark,
        )
    }
}

@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    dark: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "iconPress",
    )
    Box(
        modifier = Modifier
            .size(PikuLayout.NavHit)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(PikuLayout.NavControl)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(if (dark) GlassIconBgDark else PikuColors.surface)
                .border(
                    BorderStroke(0.5.dp, PikuColors.border),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
private fun SearchMenuButton(
    onClick: () -> Unit,
    dark: Boolean,
) {
    GlassIconButton(onClick = onClick, dark = dark) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = stringResource(R.string.search_placeholder),
            tint = if (dark) LoginTextPrimaryDark else PikuColors.textPrimary,
            modifier = Modifier.size(17.dp),
        )
    }
}

/** 头部控件行与状态栏之间的呼吸间距 */
internal val GlassHeaderTopPadding = 8.dp

@Composable
private fun UserMenuButton(
    avatarUrl: String?,
    onMenuClick: () -> Unit,
    enabled: Boolean,
    dark: Boolean,
) {
    Box(
        modifier = Modifier.size(PikuLayout.NavHit),
        contentAlignment = Alignment.Center,
    ) {
        UserAvatar(
            avatarUrl = avatarUrl,
            onClick = onMenuClick,
            dark = dark,
            size = PikuLayout.NavControl,
            enabled = enabled,
            showIndication = false,
        )
    }
}
