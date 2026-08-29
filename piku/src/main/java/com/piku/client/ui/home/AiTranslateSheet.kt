package com.piku.client.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.GTranslate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.data.local.CatalogSource
import com.piku.client.data.remote.translation.ModelEntry
import com.piku.client.data.remote.translation.Role
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.ControlAccentDark
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.LoginCardBorderDark
import com.piku.client.ui.theme.LoginCardBorderLight
import com.piku.client.ui.theme.LoginTextFaintDark
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import com.piku.client.ui.theme.themedSwitchColors

/** 弹窗内的分区小标题 */
@Composable
fun SheetSectionLabel(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiTranslateSheet(
    state: HomeUiState,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectModel: (ModelEntry) -> Unit,
    onSelectNovelModel: (ModelEntry?) -> Unit,
    onSaveCatalog: (String, String) -> Unit,
    onResetCatalog: () -> Unit,
    onActivateSource: (CatalogSource) -> Unit,
    onSaveAsSource: (String, String) -> Unit,
    onRenameSource: (CatalogSource, String) -> Unit,
    onDeleteSource: (CatalogSource) -> Unit,
    onOpenSources: () -> Unit,
    catalogOpen: Boolean = false,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    BackHandler(enabled = !catalogOpen) { onDismiss() }

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        containerColor = if (dark) LoginBackgroundDark else Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            AiTranslateMainPage(
                state = state,
                onToggleEnabled = onToggleEnabled,
                onSelectModel = onSelectModel,
                onSelectNovelModel = onSelectNovelModel,
                onOpenSources = onOpenSources,
                dark = dark,
            )
        }
    }
}

@Composable
private fun AiTranslateMainPage(
    state: HomeUiState,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectModel: (ModelEntry) -> Unit,
    onSelectNovelModel: (ModelEntry?) -> Unit,
    onOpenSources: () -> Unit,
    dark: Boolean,
) {
    val primary = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
    val faint = if (dark) LoginTextFaintDark else LoginTextFaintLight
    val accent = if (dark) ControlAccentDark else AccentDark

    var textExpanded by remember { mutableStateOf(false) }
    var novelExpanded by remember { mutableStateOf(false) }
    var imageExpanded by remember { mutableStateOf(false) }

    val blockSheetDragScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = available

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                available
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f)
            .nestedScroll(blockSheetDragScroll)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.menu_ai_translate),
            color = primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(
                    BorderStroke(0.5.dp, if (dark) LoginCardBorderDark else LoginCardBorderLight),
                    RoundedCornerShape(14.dp),
                )
                .clickable { onToggleEnabled(!state.aiTranslateEnabled) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.GTranslate,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ai_translate_enable),
                    color = primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.ai_translate_auto_subtitle),
                    color = faint,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = state.aiTranslateEnabled,
                onCheckedChange = onToggleEnabled,
                colors = themedSwitchColors(dark),
            )
        }

        Spacer(Modifier.height(18.dp))

        val usableModels = state.translateModels.filter { it.available && !it.apiKey.isNullOrBlank() }

        val isTextSelected: (ModelEntry) -> Boolean = { entry ->
            val textStoredAlive = usableModels.any { it.model == state.llmModel && it.baseUrl == state.llmBaseUrl }
            if (textStoredAlive) {
                entry.model == state.llmModel && entry.baseUrl == state.llmBaseUrl
            } else {
                entry.id == state.roleDefaultIds.text
            }
        }
        val textEntries = usableModels.filter { Role.TEXT in it.roles }
        val selectedTextLabel = textEntries.firstOrNull(isTextSelected)?.label
            ?: stringResource(R.string.ai_translate_not_selected)

        CollapsibleSection(
            title = stringResource(R.string.ai_translate_model),
            summary = selectedTextLabel,
            expanded = textExpanded,
            onToggle = { textExpanded = !textExpanded },
            dark = dark,
        ) {
            ModelListCard(
                entries = textEntries,
                isSelected = isTextSelected,
                onSelect = onSelectModel,
                dark = dark,
            )
        }

        Spacer(Modifier.height(10.dp))

        val novelModels = usableModels.filter { Role.NOVEL in it.roles }
        val isNovelSelected: (ModelEntry) -> Boolean = { entry ->
            val novelStoredAlive =
                novelModels.any { it.model == state.llmNovelModel && it.baseUrl == state.llmNovelBaseUrl }
            if (novelStoredAlive) {
                entry.model == state.llmNovelModel && entry.baseUrl == state.llmNovelBaseUrl
            } else {
                entry.id == state.roleDefaultIds.novel
            }
        }
        val novelSummary = when {
            novelModels.isEmpty() -> stringResource(R.string.ai_translate_novel_follow)
            novelModels.any(isNovelSelected) -> novelModels.first(isNovelSelected).label
            else -> stringResource(R.string.ai_translate_not_selected)
        }

        CollapsibleSection(
            title = stringResource(R.string.ai_translate_novel_model),
            summary = novelSummary,
            expanded = novelExpanded,
            onToggle = { novelExpanded = !novelExpanded },
            dark = dark,
        ) {
            if (novelModels.isEmpty()) {
                Text(
                    text = stringResource(R.string.ai_translate_novel_model_empty),
                    color = faint,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            } else {
                ModelListCard(
                    entries = novelModels,
                    isSelected = isNovelSelected,
                    onSelect = onSelectNovelModel,
                    dark = dark,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        val imageModels = usableModels.filter { Role.IMAGE in it.roles }
        val imageSummary = stringResource(R.string.ai_translate_image_count, imageModels.size)

        CollapsibleSection(
            title = stringResource(R.string.ai_translate_image_model),
            summary = imageSummary,
            expanded = imageExpanded,
            onToggle = { imageExpanded = !imageExpanded },
            dark = dark,
        ) {
            if (imageModels.isEmpty()) {
                Text(
                    text = stringResource(R.string.ai_translate_image_model_empty),
                    color = faint,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            BorderStroke(0.5.dp, if (dark) LoginCardBorderDark else LoginCardBorderLight),
                            RoundedCornerShape(14.dp),
                        ),
                ) {
                    imageModels.forEachIndexed { index, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = entry.label,
                                    color = primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (entry.hint.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = entry.hint,
                                        color = faint,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                        if (index < imageModels.lastIndex) {
                            HorizontalDivider(
                                color = if (dark) LoginCardBorderDark else LoginCardBorderLight,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 14.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(
                    BorderStroke(0.5.dp, if (dark) LoginCardBorderDark else LoginCardBorderLight),
                    RoundedCornerShape(14.dp),
                )
                .clickable(onClick = onOpenSources)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudSync,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ai_translate_catalog_source),
                    color = primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        if (state.catalogIsCustom) {
                            R.string.ai_translate_catalog_custom
                        } else {
                            R.string.ai_translate_catalog_default
                        },
                    ),
                    color = faint,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = faint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    dark: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val primary = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
    val faint = if (dark) LoginTextFaintDark else LoginTextFaintLight

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = summary,
                color = faint,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = faint,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(if (expanded) 90f else 0f),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun ModelListCard(
    entries: List<ModelEntry>,
    isSelected: (ModelEntry) -> Boolean,
    onSelect: (ModelEntry) -> Unit,
    dark: Boolean,
) {
    if (entries.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                BorderStroke(0.5.dp, if (dark) LoginCardBorderDark else LoginCardBorderLight),
                RoundedCornerShape(14.dp),
            ),
    ) {
        entries.forEachIndexed { index, entry ->
            ModelOptionRow(
                entry = entry,
                selected = isSelected(entry),
                onClick = { onSelect(entry) },
                dark = dark,
            )
            if (index < entries.lastIndex) {
                HorizontalDivider(
                    color = if (dark) LoginCardBorderDark else LoginCardBorderLight,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun ModelOptionRow(
    entry: ModelEntry,
    selected: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val primary = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
    val faint = if (dark) LoginTextFaintDark else LoginTextFaintLight
    val accent = if (dark) ControlAccentDark else AccentDark
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) accent.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.label,
                color = primary,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
            if (entry.hint.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = entry.hint,
                    color = faint,
                    fontSize = 11.sp,
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
