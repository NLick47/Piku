package com.piku.client.core.ui.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.core.storage.prefs.CatalogSource
import com.piku.client.core.storage.prefs.SettingsRepository
import com.piku.client.core.ui.common.PikuPageHeader
import com.piku.client.core.ui.theme.HomeBgBottomDark
import com.piku.client.core.ui.theme.HomeBgBottomLight
import com.piku.client.core.ui.theme.HomeBgTopDark
import com.piku.client.core.ui.theme.HomeBgTopLight
import com.piku.client.core.ui.theme.PikuColors
import com.piku.client.source.poipiku.ui.home.CatalogRefreshState
import com.piku.client.source.poipiku.ui.home.HomeUiState

private val CATALOG_KEY_HEX = Regex("^[0-9a-fA-F]{64}$")

@Composable
fun CatalogSourceScreen(
    state: HomeUiState,
    onSaveCatalog: (String, String) -> Unit,
    onResetCatalog: () -> Unit,
    onActivateSource: (CatalogSource) -> Unit,
    onSaveAsSource: (String, String) -> Unit,
    onRenameSource: (CatalogSource, String) -> Unit,
    onDeleteSource: (CatalogSource) -> Unit,
    onBack: () -> Unit,
    dark: Boolean,
) {
    BackHandler(enabled = true) { onBack() }

    val primary = PikuColors.textPrimary
    val secondary = PikuColors.textSecondary
    val accent = PikuColors.controlAccent
    val errorColor = PikuColors.error

    var urlInput by rememberSaveable(state.catalogUrl) { mutableStateOf(state.catalogUrl) }
    var keyInput by rememberSaveable(state.catalogEncKey) { mutableStateOf(state.catalogEncKey) }
    var managedSource by remember { mutableStateOf<CatalogSource?>(null) }
    val refreshing = state.catalogRefreshState is CatalogRefreshState.Loading
    val sourceSuffix = if (state.catalogSourceId.isBlank()) "" else " · ${state.catalogSourceId}"
    val trimmedKey = keyInput.trim()
    val keyValid = trimmedKey.isEmpty() || CATALOG_KEY_HEX.matches(trimmedKey)
    val saveEnabled = !refreshing && keyValid &&
        (urlInput.trim() != state.catalogUrl || trimmedKey != state.catalogEncKey)

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (dark) listOf(HomeBgTopDark, HomeBgBottomDark)
                    else listOf(HomeBgTopLight, HomeBgBottomLight),
                ),
            ),
    ) {
        PikuPageHeader(
            title = stringResource(R.string.llm_catalog_source),
            dark = dark,
            onBack = onBack,
        )
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.ai_translate_catalog_desc),
                color = secondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(22.dp))

            SettingsSectionLabel(stringResource(R.string.ai_translate_catalog_saved))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                CatalogSourceListCard(
                    sources = state.catalogSources,
                    activeUrl = state.catalogUrl,
                    activeKey = state.catalogEncKey,
                    onActivateOfficial = onResetCatalog,
                    onActivateSource = onActivateSource,
                    onManageSource = { managedSource = it },
                )
            }

            Spacer(Modifier.height(22.dp))
            SettingsCard(dark = dark) {
                Column(Modifier.padding(14.dp)) {
                    LlmTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = stringResource(R.string.ai_translate_catalog_url_label),
                        placeholder = SettingsRepository.CATALOG_URL_DEFAULT,
                        mono = true,
                    )
                    Spacer(Modifier.height(16.dp))
                    LlmTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = stringResource(R.string.ai_translate_catalog_key_label),
                        placeholder = stringResource(R.string.ai_translate_catalog_key_hint),
                        error = !keyValid,
                    )
                    FieldError(
                        if (!keyValid) stringResource(R.string.ai_translate_catalog_key_invalid) else null,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                LlmTextAction(
                    text = stringResource(R.string.ai_translate_catalog_save_as_new),
                    onClick = { onSaveAsSource(urlInput.trim(), trimmedKey) },
                    color = accent,
                    enabled = !refreshing && keyValid && urlInput.isNotBlank(),
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LlmTextAction(
                    text = stringResource(R.string.ai_translate_catalog_reset),
                    onClick = onResetCatalog,
                    color = secondary,
                    enabled = !refreshing && (state.catalogIsCustom || state.catalogEncKey.isNotEmpty()),
                )
                Spacer(Modifier.weight(1f))
                LlmActionButton(
                    text = stringResource(
                        if (refreshing) R.string.ai_translate_catalog_loading else R.string.ai_translate_catalog_save,
                    ),
                    primary = true,
                    enabled = saveEnabled,
                    onClick = { onSaveCatalog(urlInput, keyInput) },
                    modifier = Modifier.width(120.dp),
                )
            }

            when (val refresh = state.catalogRefreshState) {
                CatalogRefreshState.Idle -> Unit
                CatalogRefreshState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 2.dp, top = 10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = secondary,
                        strokeWidth = 1.5.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.ai_translate_catalog_loading),
                        color = secondary,
                        fontSize = 11.sp,
                    )
                }
                is CatalogRefreshState.Success -> Text(
                    text = stringResource(R.string.ai_translate_catalog_success, refresh.modelCount) + sourceSuffix,
                    color = secondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 2.dp, top = 10.dp),
                )
                CatalogRefreshState.UpToDate -> Text(
                    text = stringResource(R.string.ai_translate_catalog_uptodate) + sourceSuffix,
                    color = secondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 2.dp, top = 10.dp),
                )
                CatalogRefreshState.Failed -> Text(
                    text = stringResource(R.string.ai_translate_catalog_failed),
                    color = errorColor,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 2.dp, top = 10.dp),
                )
            }

            managedSource?.let { source ->
                CatalogManageDialog(
                    source = source,
                    onRename = { name ->
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty() && trimmed != source.name) onRenameSource(source, trimmed)
                        managedSource = null
                    },
                    onDelete = {
                        onDeleteSource(source)
                        managedSource = null
                    },
                    onDismiss = { managedSource = null },
                    dark = dark,
                )
            }
        }
    }
}

@Composable
private fun CatalogSourceListCard(
    sources: List<CatalogSource>,
    activeUrl: String,
    activeKey: String,
    onActivateOfficial: () -> Unit,
    onActivateSource: (CatalogSource) -> Unit,
    onManageSource: (CatalogSource) -> Unit,
) {
    val primary = PikuColors.textPrimary
    val secondary = PikuColors.textSecondary
    val borderColor = PikuColors.border
    val shape = RoundedCornerShape(12.dp)
    val officialActive =
        activeUrl in SettingsRepository.CATALOG_URL_MIRRORS && activeKey.isEmpty()

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(BorderStroke(0.5.dp, borderColor), shape),
    ) {
        val officialBg by animateColorAsState(
            targetValue = if (officialActive) PikuColors.surfaceSoft else Color.Transparent,
            label = "officialBg",
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(officialBg)
                .clickable(enabled = !officialActive, onClick = onActivateOfficial)
                .padding(start = 14.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ai_translate_catalog_default),
                    color = primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.ai_translate_catalog_official_caption),
                    color = secondary,
                    fontSize = 11.sp,
                )
            }
            if (officialActive) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        sources.forEach { source ->
            HorizontalDivider(color = borderColor, thickness = 0.5.dp)
            val active = source.url == activeUrl && source.encKey == activeKey
            val rowBg by animateColorAsState(
                targetValue = if (active) PikuColors.surfaceSoft else Color.Transparent,
                label = "sourceRowBg",
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(rowBg)
                    .clickable(enabled = !active, onClick = { onActivateSource(source) })
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = source.name,
                        color = primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = source.url,
                        color = secondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (active) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = null,
                    tint = secondary,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onManageSource(source) }
                        .padding(3.dp),
                )
            }
        }
    }
}

@Composable
private fun CatalogManageDialog(
    source: CatalogSource,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    var nameInput by rememberSaveable(source.id) { mutableStateOf(source.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PikuColors.surface,
        title = {
            Text(
                text = stringResource(R.string.ai_translate_catalog_manage_title),
                color = PikuColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        },
        text = {
            LlmTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = stringResource(R.string.ai_translate_catalog_name_hint),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(nameInput) }) {
                Text(
                    text = stringResource(R.string.ai_translate_catalog_rename),
                    color = PikuColors.controlAccent,
                    fontSize = 13.sp,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text(
                    text = stringResource(R.string.ai_translate_catalog_delete),
                    color = PikuColors.error,
                    fontSize = 13.sp,
                )
            }
        },
    )
}
