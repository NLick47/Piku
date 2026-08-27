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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.domain.model.PoipikuCategory
import com.piku.client.ui.common.UserAvatar
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.GlassIconBgDark
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.PillBorderDark
import com.piku.client.ui.theme.PillBorderLight

@Composable
internal fun GlassHeader(
    state: HomeUiState,
    avatarUrl: String?,
    onMenuClick: () -> Unit,
    menuEnabled: Boolean,
    onSearchClick: () -> Unit,
    onSelectFeedTab: (FeedTab) -> Unit,
    onCategoryClick: () -> Unit,
    onClearTag: () -> Unit,
    onDoubleTapTop: () -> Unit,
    dark: Boolean,
    isScrolling: State<Boolean>,
    drawerIsOpen: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(onDoubleTapTop) {
                detectTapGestures(onDoubleTap = { onDoubleTapTop() })
            }
            .statusBarsPadding()
            .padding(top = 8.dp),
    ) {
        LiquidGlassBackdrop(
            dark = dark,
            isScrolling = isScrolling,
            drawerIsOpen = drawerIsOpen,
            modifier = Modifier.matchParentSize(),
            translucent = state.customBackgroundPath != null,
        )
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp),
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
                if (state.feedTab == FeedTab.LATEST) {
                    Spacer(Modifier.width(8.dp))
                    CategoryMenuButton(
                        active = state.category != PoipikuCategory.ALL,
                        onClick = onCategoryClick,
                        dark = dark,
                    )
                }
            }
            FeedTabRow(
                feedTab = state.feedTab,
                currentTag = state.currentTag,
                onSelectFeedTab = onSelectFeedTab,
                onClearTag = onClearTag,
                dark = dark,
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
            .padding(start = 16.dp, end = 16.dp),
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
    val shape = RoundedCornerShape(15.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "iconPress",
    )
    Box(
        modifier = Modifier
            .size(30.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (dark) GlassIconBgDark else Color.White)
            .border(
                BorderStroke(0.5.dp, if (dark) PillBorderDark else PillBorderLight),
                shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
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
            tint = if (dark) LoginTextPrimaryDark else Color(0xFF5A5A5A),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun CategoryMenuButton(
    active: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    GlassIconButton(onClick = onClick, dark = dark) {
        Icon(
            imageVector = Icons.Outlined.GridView,
            contentDescription = stringResource(R.string.home_category_select),
            tint = when {
                active -> if (dark) LoginTextPrimaryDark else AccentDark
                dark -> LoginTextPrimaryDark.copy(alpha = 0.55f)
                else -> Color(0xFF5A5A5A)
            },
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun UserMenuButton(
    avatarUrl: String?,
    onMenuClick: () -> Unit,
    enabled: Boolean,
    dark: Boolean,
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .drawBehind {
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF9A7FC9).copy(alpha = if (dark) 0.24f else 0.18f),
                            Color.Transparent,
                        ),
                    ),
                    radius = size.minDimension / 2f * 1.25f,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        UserAvatar(
            avatarUrl = avatarUrl,
            onClick = onMenuClick,
            dark = dark,
            enabled = enabled,
            showIndication = false,
        )
    }
}
