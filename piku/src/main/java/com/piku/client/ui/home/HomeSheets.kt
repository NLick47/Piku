package com.piku.client.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.domain.model.AppLanguage
import com.piku.client.domain.model.PoipikuCategory
import com.piku.client.domain.model.ThemeMode
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.LoginCardBorderDark
import com.piku.client.ui.theme.LoginCardBorderLight
import com.piku.client.ui.theme.LoginCardDark
import com.piku.client.ui.theme.LoginTextFaintDark
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.LoginTextSecondaryLight
import com.piku.client.ui.theme.themedSwitchColors

internal data class CategoryGroup(
    val titleRes: Int,
    val categories: List<PoipikuCategory>,
)

private val CATEGORY_GROUPS = listOf(
    CategoryGroup(
        R.string.category_group_practice,
        listOf(
            PoipikuCategory.RAKUGAKI,
            PoipikuCategory.JISHUREN,
            PoipikuCategory.RIHABIRI,
        ),
    ),
    CategoryGroup(
        R.string.category_group_share,
        listOf(
            PoipikuCategory.DEKITA,
            PoipikuCategory.KAKO_WO_SARASU,
            PoipikuCategory.KUYOU,
        ),
    ),
    CategoryGroup(
        R.string.category_group_wip,
        listOf(
            PoipikuCategory.SAGYOSHINCHOKU,
            PoipikuCategory.KAKIKAKE,
            PoipikuCategory.KAKENEE,
        ),
    ),
    CategoryGroup(
        R.string.category_group_community,
        listOf(
            PoipikuCategory.OSHIRASE,
            PoipikuCategory.MEMO,
            PoipikuCategory.NETABARE,
            PoipikuCategory.SHIRIWOTATAKU,
            PoipikuCategory.OSHINAGAKI,
        ),
    ),
)

private fun retentionDaysRes(days: Int): Int = when (days) {
    7 -> R.string.retention_7d
    30 -> R.string.retention_30d
    90 -> R.string.retention_90d
    else -> R.string.retention_forever
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun CategorySheet(
    selected: PoipikuCategory,
    onSelect: (PoipikuCategory) -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginBackgroundDark else Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.home_category_select),
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.home_category_current,
                    stringResource(selected.nameRes),
                ),
                color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))
            AllCategoriesButton(
                active = selected == PoipikuCategory.ALL,
                onClick = { onSelect(PoipikuCategory.ALL) },
                dark = dark,
            )
            Spacer(Modifier.height(20.dp))
            CATEGORY_GROUPS.forEach { group ->
                Text(
                    text = stringResource(group.titleRes),
                    color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    group.categories.forEach { category ->
                        CategoryPill(
                            text = stringResource(category.nameRes),
                            active = selected == category,
                            onClick = { onSelect(category) },
                            dark = dark,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeModeSheet(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginBackgroundDark else Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.theme_select_title),
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            SettingsOptionRow(
                text = stringResource(R.string.theme_mode_system),
                selected = selected == ThemeMode.SYSTEM,
                onClick = { onSelect(ThemeMode.SYSTEM) },
                dark = dark,
            )
            Spacer(Modifier.height(8.dp))
            SettingsOptionRow(
                text = stringResource(R.string.theme_mode_light),
                selected = selected == ThemeMode.LIGHT,
                onClick = { onSelect(ThemeMode.LIGHT) },
                dark = dark,
            )
            Spacer(Modifier.height(8.dp))
            SettingsOptionRow(
                text = stringResource(R.string.theme_mode_dark),
                selected = selected == ThemeMode.DARK,
                onClick = { onSelect(ThemeMode.DARK) },
                dark = dark,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RetentionSheet(
    selectedDays: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginBackgroundDark else Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.retention_select_title),
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.retention_select_hint),
                color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))
            listOf(7, 30, 90, 0).forEach { days ->
                SettingsOptionRow(
                    text = stringResource(retentionDaysRes(days)),
                    selected = selectedDays == days,
                    onClick = { onSelect(days) },
                    dark = dark,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LanguageSheet(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginBackgroundDark else Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.language_select_title),
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            AppLanguage.entries.forEach { language ->
                SettingsOptionRow(
                    text = stringResource(language.labelRes()),
                    selected = selected == language,
                    onClick = { onSelect(language) },
                    dark = dark,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
internal fun LogoutConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginCardDark else Color.White,
        title = {
            Text(
                text = stringResource(R.string.logout),
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.logout_confirm_message),
                color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
                fontSize = 13.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.logout_confirm),
                    color = if (dark) Color(0xFFE08A8A) else Color(0xFFC24B4B),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.detail_favorite_cancel),
                    color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                )
            }
        },
    )
}

@Composable
private fun SettingsOptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    val container = when {
        selected -> if (dark) {
            LoginTextPrimaryDark.copy(alpha = 0.14f)
        } else {
            LoginTextPrimaryLight.copy(alpha = 0.07f)
        }
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(BorderStroke(0.5.dp, if (dark) LoginCardBorderDark else LoginCardBorderLight), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = AccentDark,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun AllCategoriesButton(
    active: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    val container by animateColorAsState(
        targetValue = when {
            active -> if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
            else -> if (dark) LoginCardDark else Color(0xFF000000).copy(alpha = 0.03f)
        },
        label = "allBtnBg",
    )
    val content by animateColorAsState(
        targetValue = when {
            active -> if (dark) LoginBackgroundDark else Color.White
            else -> if (dark) LoginTextSecondaryDark else Color(0xFF5A5A5A)
        },
        label = "allBtnText",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(BorderStroke(0.5.dp, if (dark) LoginCardBorderDark else LoginCardBorderLight), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_category_all),
            color = content,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (active) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CategoryPill(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(20.dp)
    val container by animateColorAsState(
        targetValue = when {
            active -> if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
            else -> if (dark) LoginCardDark else Color(0xFF000000).copy(alpha = 0.04f)
        },
        label = "pillBg",
    )
    val content by animateColorAsState(
        targetValue = when {
            active -> if (dark) LoginBackgroundDark else Color.White
            else -> if (dark) LoginTextSecondaryDark else Color(0xFF5A5A5A)
        },
        label = "pillText",
    )
    Row(
        modifier = Modifier
            .clip(shape)
            .then(if (active) Modifier.shadow(3.dp, shape) else Modifier)
            .background(container)
            .border(BorderStroke(0.5.dp, if (dark) LoginCardBorderDark else LoginCardBorderLight), shape)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = if (active) 9.dp else 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = content,
            fontSize = 13.sp,
        )
        androidx.compose.animation.AnimatedVisibility(
            visible = active,
            enter = androidx.compose.animation.scaleIn(
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
                ),
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(14.dp),
            )
        }
    }
}
