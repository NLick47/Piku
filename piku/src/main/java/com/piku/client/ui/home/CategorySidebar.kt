package com.piku.client.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.domain.model.PoipikuCategory
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.PikuColors

@Composable
internal fun CategorySidebar(
    selected: PoipikuCategory,
    onSelect: (PoipikuCategory) -> Unit,
    dark: Boolean,
) {
    Column(
        Modifier
            .width(176.dp)
            .fillMaxHeight()
            .background(PikuColors.surface)
            .border(0.5.dp, PikuColors.border)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(vertical = 12.dp),
    ) {
        SidebarItem(
            text = stringResource(R.string.home_category_all),
            active = selected == PoipikuCategory.ALL,
            onClick = { onSelect(PoipikuCategory.ALL) },
            dark = dark,
        )
        PoipikuCategory.entries.filter { it != PoipikuCategory.ALL }.forEach { category ->
            SidebarItem(
                text = stringResource(category.nameRes),
                active = selected == category,
                onClick = { onSelect(category) },
                dark = dark,
            )
        }
    }
}

@Composable
private fun SidebarItem(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(12.dp)
    val background by animateColorAsState(
        targetValue = when {
            active -> if (dark) {
                LoginTextPrimaryDark.copy(alpha = 0.14f)
            } else {
                LoginTextPrimaryLight.copy(alpha = 0.07f)
            }
            else -> Color.Transparent
        },
        label = "sidebarBg",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (active) {
                        PikuColors.textPrimary
                    } else {
                        Color.Transparent
                    },
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (dark) LoginTextSecondaryDark else Color(0xFF5A5A5A),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
