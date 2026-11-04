package com.piku.client.core.ui.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.core.translation.AuthMode
import com.piku.client.core.translation.ManualProvider
import com.piku.client.core.translation.ManualProviderPreset
import com.piku.client.core.translation.ModelEntry
import com.piku.client.core.translation.Role
import com.piku.client.core.translation.isUsable
import com.piku.client.core.ui.common.PikuBottomSheet
import com.piku.client.core.ui.common.PikuPageHeader
import com.piku.client.core.ui.theme.HomeBgBottomDark
import com.piku.client.core.ui.theme.HomeBgBottomLight
import com.piku.client.core.ui.theme.HomeBgTopDark
import com.piku.client.core.ui.theme.HomeBgTopLight
import com.piku.client.core.ui.theme.PikuColors
import com.piku.client.core.ui.theme.themedSwitchColors
import com.piku.client.source.poipiku.ui.home.HomeUiState
import com.piku.client.source.poipiku.ui.home.ProviderTestState

internal data class ProviderEditorRequest(
    val provider: ManualProvider,
    val isNew: Boolean,
    val presetId: String? = null,
)

private sealed interface LlmPage {
    data object Main : LlmPage
    data object ManualList : LlmPage
    data object BuiltinPrompts : LlmPage
    data class Editor(val request: ProviderEditorRequest, val returnToManual: Boolean) : LlmPage
}

@Composable
fun LlmConfigScreen(
    state: HomeUiState,
    onSelectEntry: (String, ModelEntry) -> Unit,
    onClearSelection: (String) -> Unit,
    onSaveProvider: (ManualProvider, String?, Boolean) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onTestProvider: (String) -> Unit,
    onTestDraft: (ManualProvider, String?) -> Unit,
    onClearDraftTest: () -> Unit,
    onFetchModels: (ManualProvider, String?) -> Unit,
    onClearModelFetch: () -> Unit,
    onOpenCatalogSource: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleTagsAuto: (Boolean) -> Unit,
    onBack: () -> Unit,
    dark: Boolean,
) {
    var page by remember { mutableStateOf<LlmPage>(LlmPage.Main) }
    var rolePicker by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ManualProvider?>(null) }

    fun openAdd(
        role: String? = null,
        preset: ManualProviderPreset? = null,
        returnToManual: Boolean = false,
    ) {
        onClearDraftTest()
        onClearModelFetch()
        page = LlmPage.Editor(
            request = ProviderEditorRequest(
                provider = preset?.toDraft() ?: ManualProvider(
                    authMode = AuthMode.NONE,
                    roles = listOf(role?.takeIf { it != Role.IMAGE } ?: Role.TEXT),
                ),
                isNew = true,
                presetId = preset?.id ?: ManualProviderPreset.CUSTOM_ID,
            ),
            returnToManual = returnToManual,
        )
    }

    fun openEdit(provider: ManualProvider, returnToManual: Boolean) {
        onClearDraftTest()
        onClearModelFetch()
        page = LlmPage.Editor(
            request = ProviderEditorRequest(provider, isNew = false),
            returnToManual = returnToManual,
        )
    }

    fun leaveEditor(returnToManual: Boolean) {
        page = if (returnToManual) LlmPage.ManualList else LlmPage.Main
    }

    BackHandler(enabled = true) {
        when (val current = page) {
            LlmPage.Main -> onBack()
            LlmPage.ManualList, LlmPage.BuiltinPrompts -> page = LlmPage.Main
            is LlmPage.Editor -> leaveEditor(current.returnToManual)
        }
    }

    when (val current = page) {
        LlmPage.Main -> MainSettingsPage(
            state = state,
            dark = dark,
            onToggleEnabled = onToggleEnabled,
            onToggleTagsAuto = onToggleTagsAuto,
            onPickRole = { rolePicker = it },
            onOpenManual = { page = LlmPage.ManualList },
            onOpenBuiltinPrompts = { page = LlmPage.BuiltinPrompts },
            onOpenCatalogSource = onOpenCatalogSource,
            onBack = onBack,
        )

        LlmPage.ManualList -> ManualProvidersPage(
            state = state,
            dark = dark,
            onBack = { page = LlmPage.Main },
            onQuickAdd = { preset -> openAdd(preset = preset, returnToManual = true) },
            onCustomAdd = { openAdd(returnToManual = true) },
            onEdit = { provider -> openEdit(provider, returnToManual = true) },
            onTest = onTestProvider,
        )

        LlmPage.BuiltinPrompts -> BuiltinPromptScreen(
            state = state,
            dark = dark,
            onBack = { page = LlmPage.Main },
        )

        is LlmPage.Editor -> ManualProviderEditorScreen(
            provider = current.request.provider,
            isNew = current.request.isNew,
            initialPresetId = current.request.presetId,
            testState = state.providerTestStates[ProviderTestState.DRAFT_TEST_KEY],
            modelFetchState = state.modelFetchState,
            builtinTextPrompt = state.builtinTextPrompt,
            builtinNovelPrompt = state.builtinNovelPrompt,
            builtinTextFromCatalog = state.builtinTextFromCatalog,
            builtinNovelFromCatalog = state.builtinNovelFromCatalog,
            promptLangKey = state.promptLangKey,
            onTest = onTestDraft,
            onFetchModels = onFetchModels,
            onClearModelFetch = onClearModelFetch,
            onSave = { provider, key, clearKey ->
                onSaveProvider(provider, key, clearKey)
                leaveEditor(current.returnToManual)
            },
            onRequestDelete = { deleteTarget = it },
            onDismiss = { leaveEditor(current.returnToManual) },
            onClearTest = onClearDraftTest,
            dark = dark,
        )
    }

    rolePicker?.let { role ->
        RolePickerSheet(
            role = role,
            state = state,
            dark = dark,
            onSelectEntry = {
                rolePicker = null
                onSelectEntry(role, it)
            },
            onClear = {
                rolePicker = null
                onClearSelection(role)
            },
            onAddProvider = {
                rolePicker = null
                openAdd(role = role)
            },
            onEditProvider = { provider ->
                rolePicker = null
                openEdit(provider, returnToManual = false)
            },
            onDismiss = { rolePicker = null },
        )
    }

    deleteTarget?.let { provider ->
        val returnToManual = (page as? LlmPage.Editor)?.returnToManual == true
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = {
                Text(
                    text = stringResource(R.string.llm_provider_delete_confirm_title),
                    color = PikuColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.llm_provider_delete_confirm_text),
                    color = PikuColors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProvider(provider.id)
                        deleteTarget = null
                        leaveEditor(returnToManual)
                    },
                ) {
                    Text(stringResource(R.string.llm_provider_delete_confirm), color = PikuColors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.llm_provider_cancel), color = PikuColors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun MainSettingsPage(
    state: HomeUiState,
    dark: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleTagsAuto: (Boolean) -> Unit,
    onPickRole: (String) -> Unit,
    onOpenManual: () -> Unit,
    onOpenBuiltinPrompts: () -> Unit,
    onOpenCatalogSource: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(pageBackground(dark)),
    ) {
        PikuPageHeader(
            title = stringResource(R.string.menu_ai_translate),
            subtitle = stringResource(R.string.llm_config_subtitle),
            dark = dark,
            onBack = onBack,
        )
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 28.dp),
        ) {
            TranslateSettingsCard(
                state = state,
                dark = dark,
                onToggleEnabled = onToggleEnabled,
                onToggleTagsAuto = onToggleTagsAuto,
            )

            Spacer(Modifier.height(20.dp))
            SettingsSectionLabel(stringResource(R.string.llm_section_models))
            ModelBindingsCard(state = state, onPick = onPickRole, dark = dark)

            Spacer(Modifier.height(20.dp))
            SettingsSectionLabel(stringResource(R.string.llm_section_interface))
            InterfaceCard(
                state = state,
                dark = dark,
                onOpenManual = onOpenManual,
                onOpenCatalogSource = onOpenCatalogSource,
            )

            Spacer(Modifier.height(20.dp))
            SettingsSectionLabel(stringResource(R.string.llm_section_advanced))
            SettingsCard(dark = dark) {
                InterfaceRow(
                    title = stringResource(R.string.llm_builtin_prompt_title),
                    subtitle = stringResource(R.string.llm_builtin_prompt_desc),
                    onClick = onOpenBuiltinPrompts,
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.llm_builtin_footnote),
                color = PikuColors.textFaint,
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun pageBackground(dark: Boolean): Brush = Brush.verticalGradient(
    if (dark) listOf(HomeBgTopDark, HomeBgBottomDark)
    else listOf(HomeBgTopLight, HomeBgBottomLight),
)

@Composable
private fun TranslateSettingsCard(
    state: HomeUiState,
    dark: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleTagsAuto: (Boolean) -> Unit,
) {
    SettingsCard(dark = dark) {
        TranslateToggleRow(
            title = stringResource(R.string.ai_translate_enable),
            subtitle = stringResource(R.string.ai_translate_auto_subtitle),
            checked = state.aiTranslateEnabled,
            onToggle = onToggleEnabled,
            dark = dark,
        )
        if (state.aiTranslateEnabled) {
            HorizontalDivider(
                color = PikuColors.border,
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            TranslateToggleRow(
                title = stringResource(R.string.ai_translate_tags_auto),
                subtitle = null,
                checked = state.autoTranslateTags,
                onToggle = onToggleTagsAuto,
                dark = dark,
                compact = true,
            )
        }
    }
}

@Composable
private fun TranslateToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    dark: Boolean,
    compact: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(
                horizontal = 16.dp,
                vertical = if (compact) 11.dp else 14.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = PikuColors.textPrimary,
                fontSize = if (compact) 13.5.sp else 14.5.sp,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = PikuColors.textSecondary,
                    fontSize = 11.5.sp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = themedSwitchColors(dark),
        )
    }
}

@Composable
private fun ModelBindingsCard(
    state: HomeUiState,
    onPick: (String) -> Unit,
    dark: Boolean,
) {
    SettingsCard(dark = dark) {
        listOf(Role.TEXT, Role.NOVEL, Role.IMAGE).forEachIndexed { index, role ->
            if (index > 0) {
                HorizontalDivider(
                    color = PikuColors.border,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
            ModelBindingRow(
                role = role,
                state = state,
                onClick = { onPick(role) },
            )
        }
    }
}

@Composable
private fun ModelBindingRow(
    role: String,
    state: HomeUiState,
    onClick: () -> Unit,
) {
    val entry = selectedLlmEntry(state, role)
    val missing = entry != null && !entry.isUsable()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = roleLabel(role),
                color = PikuColors.textSecondary,
                fontSize = 10.5.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry?.label?.ifBlank { entry.baseUrl }
                    ?: stringResource(R.string.llm_source_none),
                color = if (missing) PikuColors.error else PikuColors.textPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            missing -> MiniPill(
                stringResource(R.string.llm_manual_key_missing),
                color = PikuColors.error,
            )
            entry?.isManual == true -> MiniPill(stringResource(R.string.llm_source_manual))
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = PikuColors.textFaint,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun InterfaceCard(
    state: HomeUiState,
    dark: Boolean,
    onOpenManual: () -> Unit,
    onOpenCatalogSource: () -> Unit,
) {
    SettingsCard(dark = dark) {
        InterfaceRow(
            title = stringResource(R.string.llm_config_tab_manual),
            subtitle = if (state.manualProviders.isEmpty()) {
                stringResource(R.string.llm_manual_entry_empty)
            } else {
                stringResource(
                    R.string.llm_manual_entry_count,
                    state.manualProviders.size,
                )
            },
            onClick = onOpenManual,
        )
        HorizontalDivider(
            color = PikuColors.border,
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        InterfaceRow(
            title = stringResource(R.string.llm_catalog_source),
            subtitle = stringResource(
                if (state.catalogIsCustom) {
                    R.string.llm_catalog_custom
                } else {
                    R.string.llm_catalog_default
                },
            ),
            onClick = onOpenCatalogSource,
        )
    }
}

@Composable
private fun InterfaceRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = PikuColors.textPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = PikuColors.textSecondary,
                fontSize = 10.5.sp,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = PikuColors.textFaint,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun ManualProvidersPage(
    state: HomeUiState,
    dark: Boolean,
    onBack: () -> Unit,
    onQuickAdd: (ManualProviderPreset) -> Unit,
    onCustomAdd: () -> Unit,
    onEdit: (ManualProvider) -> Unit,
    onTest: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(pageBackground(dark)),
    ) {
        PikuPageHeader(
            title = stringResource(R.string.llm_config_tab_manual),
            subtitle = stringResource(R.string.llm_manual_page_subtitle),
            dark = dark,
            onBack = onBack,
        )
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 28.dp),
        ) {
            SettingsSectionLabel(stringResource(R.string.llm_manual_quick_add))
            QuickAddGrid(
                onQuickAdd = onQuickAdd,
                onCustomAdd = onCustomAdd,
            )

            Spacer(Modifier.height(20.dp))
            if (state.manualProviders.isEmpty()) {
                Text(
                    text = stringResource(R.string.llm_manual_empty_desc),
                    color = PikuColors.textSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            } else {
                SettingsSectionLabel(
                    stringResource(
                        R.string.llm_manual_added_count,
                        state.manualProviders.size,
                    ),
                )
                state.manualProviders.forEach { provider ->
                    ManualProviderCard(
                        provider = provider,
                        state = state,
                        dark = dark,
                        onTestProvider = onTest,
                        onEditProvider = onEdit,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            ManualInfoNote()
        }
    }
}

@Composable
private fun QuickAddGrid(
    onQuickAdd: (ManualProviderPreset) -> Unit,
    onCustomAdd: () -> Unit,
) {
    Column {
        ManualProviderPreset.QUICK_ADD.chunked(2).forEach { row ->
            Row {
                row.forEach { preset ->
                    QuickAddTile(
                        preset = preset,
                        modifier = Modifier.weight(1f),
                        onClick = { onQuickAdd(preset) },
                    )
                    if (row.size == 1) {
                        Spacer(Modifier.weight(1f))
                    } else if (preset != row.last()) {
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(PikuColors.surface)
                .border(BorderStroke(0.5.dp, PikuColors.border), RoundedCornerShape(15.dp))
                .clickable(onClick = onCustomAdd)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.llm_manual_custom),
                    color = PikuColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.llm_manual_custom_desc),
                    color = PikuColors.textSecondary,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "+",
                color = PikuColors.textFaint,
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
private fun QuickAddTile(
    preset: ManualProviderPreset,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(PikuColors.surface)
            .border(BorderStroke(0.5.dp, PikuColors.border), RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = preset.name,
                color = PikuColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = "+", color = PikuColors.textFaint, fontSize = 15.sp)
        }
        Text(
            text = preset.hint,
            color = PikuColors.textSecondary,
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun ManualProviderCard(
    provider: ManualProvider,
    state: HomeUiState,
    dark: Boolean,
    onTestProvider: (String) -> Unit,
    onEditProvider: (ManualProvider) -> Unit,
) {
    val primary = PikuColors.textPrimary
    val secondary = PikuColors.textSecondary
    val test = state.providerTestStates[provider.id]
    val roleText = provider.roles.map { roleLabel(it) }.joinToString(" / ")
    SettingsCard(dark = dark) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(test = test, provider = provider)
                Spacer(Modifier.width(7.dp))
                Text(
                    text = provider.name,
                    color = primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                MiniPill(authLabel(provider))
                Spacer(Modifier.width(2.dp))
                TextButton(
                    onClick = { onEditProvider(provider) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = stringResource(R.string.llm_manual_edit),
                        color = secondary,
                        fontSize = 11.sp,
                    )
                }
            }
            Text(
                text = provider.baseUrl,
                color = secondary,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = stringResource(R.string.llm_provider_model_label, provider.model) +
                    if (roleText.isEmpty()) "" else " · $roleText",
                color = secondary,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (provider.promptOverrides.hasAny()) {
                Spacer(Modifier.height(4.dp))
                MiniPill(
                    stringResource(R.string.llm_provider_prompt_customized),
                    color = PikuColors.controlAccent,
                )
            }
            HorizontalDivider(
                color = PikuColors.border,
                thickness = 0.5.dp,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onTestProvider(provider.id) },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = stringResource(R.string.llm_manual_test),
                        color = PikuColors.controlAccent,
                        fontSize = 11.5.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                TestStatusText(test = test, provider = provider)
            }
        }
    }
}

@Composable
private fun ManualInfoNote() {
    Text(
        text = stringResource(R.string.llm_manual_info),
        color = PikuColors.textSecondary,
        fontSize = 10.5.sp,
        lineHeight = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(PikuColors.surfaceMuted)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun StatusDot(test: ProviderTestState?, provider: ManualProvider) {
    val color = when (test) {
        ProviderTestState.Testing -> Color(0xFFFFA726)
        is ProviderTestState.Success -> Color(0xFF5BAE61)
        is ProviderTestState.Failed -> PikuColors.error
        else -> if (provider.authMode != AuthMode.NONE && !provider.hasKey) {
            Color(0xFFFFA726)
        } else {
            PikuColors.textFaint
        }
    }
    Box(
        Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RolePickerSheet(
    role: String,
    state: HomeUiState,
    dark: Boolean,
    onSelectEntry: (ModelEntry) -> Unit,
    onClear: () -> Unit,
    onAddProvider: () -> Unit,
    onEditProvider: (ManualProvider) -> Unit,
    onDismiss: () -> Unit,
) {
    val builtin = state.translateModels.filter {
        !it.isManual && role in it.roles && it.isUsable()
    }
    val manual = state.manualProviders.filter { role in it.roles }
    val stored = storedSelection(state, role)
    PikuBottomSheet(
        onDismissRequest = onDismiss,
        dark = dark,
        scrollable = true,
    ) {
        Text(
            text = stringResource(R.string.llm_role_picker_title, roleLabel(role)),
            color = PikuColors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = roleDesc(role),
            color = PikuColors.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(12.dp))
        PickerRow(
            title = stringResource(R.string.llm_role_default_option),
            subtitle = stringResource(R.string.llm_role_default_desc),
            selected = stored.isBlank(),
            onClick = onClear,
        )
        HorizontalDivider(color = PikuColors.border, thickness = 0.5.dp)
        OptionGroupLabel(stringResource(R.string.llm_role_group_builtin))
        if (builtin.isEmpty()) {
            Text(
                text = stringResource(R.string.llm_role_builtin_empty),
                color = PikuColors.textSecondary,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        } else {
            builtin.forEach { entry ->
                ModelRow(
                    entry = entry,
                    selected = stored.isNotBlank() && selectedLlmEntry(state, role)?.id == entry.id,
                    onClick = { onSelectEntry(entry) },
                )
            }
        }
        OptionGroupLabel(stringResource(R.string.llm_role_group_manual))
        if (manual.isEmpty()) {
            Text(
                text = stringResource(
                    if (role == Role.IMAGE) {
                        R.string.llm_image_manual_unsupported
                    } else {
                        R.string.llm_role_manual_empty
                    },
                ),
                color = PikuColors.textSecondary,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
            if (role != Role.IMAGE) {
                TextButton(
                    onClick = onAddProvider,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text(stringResource(R.string.llm_manual_add), fontSize = 11.5.sp)
                }
            }
        } else {
            manual.forEach { provider ->
                val entry = state.translateModels.firstOrNull { it.id == provider.id }
                if (entry != null) {
                    ModelRow(
                        entry = entry,
                        selected = selectedLlmEntry(state, role)?.id == provider.id,
                        onClick = {
                            if (entry.isUsable()) onSelectEntry(entry) else onEditProvider(provider)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun PickerRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioDot(selected = selected)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) PikuColors.textPrimary else PikuColors.textFaint,
                fontSize = 13.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = PikuColors.textSecondary,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = PikuColors.controlAccent,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun ModelRow(
    entry: ModelEntry,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioDot(selected = selected)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.label,
                color = if (enabled) PikuColors.textPrimary else PikuColors.textFaint,
                fontSize = 13.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.hint.ifBlank { entry.baseUrl },
                color = PikuColors.textSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (entry.isManual) {
            MiniPill(stringResource(R.string.llm_source_manual))
        } else if (entry.free) {
            Text(
                text = stringResource(R.string.llm_badge_free),
                color = Color(0xFF5BAE61),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (!entry.isUsable()) {
            Spacer(Modifier.width(4.dp))
            MiniPill(stringResource(R.string.llm_manual_key_missing), color = PikuColors.error)
        }
        if (selected) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = PikuColors.controlAccent,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun OptionGroupLabel(text: String) {
    Text(
        text = text,
        color = PikuColors.textFaint,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 5.dp),
    )
}

@Composable
private fun RadioDot(selected: Boolean) {
    val border = if (selected) PikuColors.textPrimary else PikuColors.border
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .border(BorderStroke(1.5.dp, border), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(PikuColors.textPrimary),
            )
        }
    }
}

private fun storedSelection(state: HomeUiState, role: String): String = when (role) {
    Role.TEXT -> state.llmModel
    Role.NOVEL -> state.llmNovelModel
    Role.IMAGE -> state.llmImageModel
    else -> ""
}

internal fun selectedLlmEntry(state: HomeUiState, role: String): ModelEntry? {
    val stored = storedSelection(state, role)
    if (stored.isNotBlank()) {
        state.translateModels.firstOrNull {
            (it.id == stored || it.model == stored) && role in it.roles && it.available
        }?.let { return it }
    }
    val defaultId = when (role) {
        Role.TEXT -> state.roleDefaultIds.text
        Role.NOVEL -> state.roleDefaultIds.novel
        Role.IMAGE -> state.roleDefaultIds.image
        else -> null
    }
    return state.translateModels.firstOrNull {
        it.id == defaultId && role in it.roles && it.available
    }
}

@Composable
private fun roleDesc(role: String): String = stringResource(
    when (role) {
        Role.TEXT -> R.string.llm_role_text_desc
        Role.NOVEL -> R.string.llm_role_novel_desc
        Role.IMAGE -> R.string.llm_role_image_desc
        else -> R.string.llm_role_text_desc
    },
)
