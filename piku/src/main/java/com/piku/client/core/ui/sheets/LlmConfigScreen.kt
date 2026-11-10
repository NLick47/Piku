package com.piku.client.core.ui.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.core.translation.ManualProvider
import com.piku.client.core.translation.ManualProviderPreset
import com.piku.client.core.translation.ModelEntry
import com.piku.client.core.translation.Role
import com.piku.client.core.translation.isUsable
import com.piku.client.core.ui.common.PikuBottomSheet
import com.piku.client.core.ui.common.PikuPageHeader
import com.piku.client.core.ui.common.PikuSheetSubtitle
import com.piku.client.core.ui.common.PikuSheetTitle
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
    onCancelTest: (String) -> Unit,
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
                    authMode = com.piku.client.core.translation.AuthMode.NONE,
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
            onCancelTest = onCancelTest,
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
            onCancelDraftTest = { onCancelTest(ProviderTestState.DRAFT_TEST_KEY) },
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
            containerColor = PikuColors.surface,
            title = {
                Text(
                    text = stringResource(R.string.llm_provider_delete_confirm_title),
                    color = PikuColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.llm_provider_delete_confirm_text),
                    color = PikuColors.textSecondary,
                    fontSize = 13.sp,
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
    val modelCount = state.translateModels.count { it.isUsable() }
    val headerSubtitle = if (state.aiTranslateEnabled) {
        stringResource(R.string.llm_status_on, modelCount)
    } else {
        stringResource(R.string.llm_status_off)
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(pageBackground(dark)),
    ) {
        PikuPageHeader(
            title = stringResource(R.string.menu_ai_translate),
            subtitle = headerSubtitle,
            dark = dark,
            onBack = onBack,
        )
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 28.dp),
        ) {
            OverviewCard(
                state = state,
                dark = dark,
                onToggleEnabled = onToggleEnabled,
                onToggleTagsAuto = onToggleTagsAuto,
                onPickRole = onPickRole,
            )

            Spacer(Modifier.height(22.dp))
            SettingsSectionLabel(stringResource(R.string.llm_section_interface))
            SettingsCard(dark = dark) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    LlmEntryRow(
                        title = stringResource(R.string.llm_config_tab_manual),
                        subtitle = if (state.manualProviders.isEmpty()) {
                            stringResource(R.string.llm_manual_entry_empty)
                        } else {
                            stringResource(R.string.llm_manual_entry_count, state.manualProviders.size)
                        },
                        onClick = onOpenManual,
                    )
                    LlmEntryRow(
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

            Spacer(Modifier.height(22.dp))
            SettingsSectionLabel(stringResource(R.string.llm_section_advanced))
            SettingsCard(dark = dark) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    LlmEntryRow(
                        title = stringResource(R.string.llm_builtin_prompt_title),
                        subtitle = stringResource(R.string.llm_builtin_prompt_desc),
                        onClick = onOpenBuiltinPrompts,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.llm_builtin_footnote),
                color = PikuColors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 2.dp),
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
private fun OverviewCard(
    state: HomeUiState,
    dark: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleTagsAuto: (Boolean) -> Unit,
    onPickRole: (String) -> Unit,
) {
    SettingsCard(dark = dark) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.llm_config_master_label),
                        color = PikuColors.textFaint,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.ai_translate_auto_subtitle),
                        color = PikuColors.textPrimary,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = state.aiTranslateEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = themedSwitchColors(dark),
                )
            }
            if (!state.aiTranslateEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.llm_disabled_hint),
                    color = PikuColors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
            }

            LlmDivider()
            listOf(Role.TEXT, Role.NOVEL, Role.IMAGE).forEach { role ->
                ModelBindingRow(role = role, state = state, onClick = { onPickRole(role) })
            }

            if (state.aiTranslateEnabled) {
                LlmDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.ai_translate_tags_auto),
                        color = PikuColors.textPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.autoTranslateTags,
                        onCheckedChange = onToggleTagsAuto,
                        colors = themedSwitchColors(dark),
                    )
                }
            }
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
    val label = entry?.label?.ifBlank { entry.baseUrl }
        ?: stringResource(R.string.llm_source_none)
    val text = if (missing) {
        "$label · ${stringResource(R.string.llm_manual_key_missing)}"
    } else {
        label
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = roleLabel(role),
                    color = PikuColors.textFaint,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (storedSelection(state, role).isBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.llm_following_default),
                        color = PikuColors.textFaint,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = text,
                color = when {
                    missing -> PikuColors.error
                    entry == null -> PikuColors.textSecondary
                    else -> PikuColors.textPrimary
                },
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LlmChevron()
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
    onCancelTest: (String) -> Unit,
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
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 28.dp),
        ) {
            SettingsSectionLabel(stringResource(R.string.llm_manual_quick_add))
            QuickAddGrid(onQuickAdd = onQuickAdd, onCustomAdd = onCustomAdd)

            Spacer(Modifier.height(22.dp))
            if (state.manualProviders.isEmpty()) {
                Text(
                    text = stringResource(R.string.llm_manual_empty_desc),
                    color = PikuColors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            } else {
                SettingsSectionLabel(
                    stringResource(R.string.llm_manual_added_count, state.manualProviders.size),
                )
                state.manualProviders.forEach { provider ->
                    ManualProviderCard(
                        provider = provider,
                        state = state,
                        dark = dark,
                        onTestProvider = onTest,
                        onCancelTest = onCancelTest,
                        onEditProvider = onEdit,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.llm_manual_info),
                color = PikuColors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
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
        val shape = RoundedCornerShape(10.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(PikuColors.surface)
                .border(BorderStroke(0.5.dp, PikuColors.border), shape)
                .clickable(onClick = onCustomAdd)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.llm_manual_custom),
                    color = PikuColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.llm_manual_custom_desc),
                    color = PikuColors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = PikuColors.textFaint,
                modifier = Modifier.size(15.dp),
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
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(PikuColors.surface)
            .border(BorderStroke(0.5.dp, PikuColors.border), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = preset.name,
                color = PikuColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = PikuColors.textFaint,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = preset.hint,
            color = PikuColors.textSecondary,
            fontSize = 11.sp,
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
    onCancelTest: (String) -> Unit,
    onEditProvider: (ManualProvider) -> Unit,
) {
    val test = state.providerTestStates[provider.id]
    val roleText = provider.roles.map { roleLabel(it) }.joinToString(" / ")
    SettingsCard(dark = dark) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = provider.name,
                    color = PikuColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                MiniPill(authLabel(provider))
                Spacer(Modifier.width(8.dp))
                LlmTextAction(
                    text = stringResource(R.string.llm_manual_edit),
                    onClick = { onEditProvider(provider) },
                )
            }
            Text(
                text = provider.baseUrl,
                color = PikuColors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 7.dp),
            )
            Text(
                text = stringResource(R.string.llm_provider_model_label, provider.model) +
                    if (roleText.isEmpty()) "" else " · $roleText",
                color = PikuColors.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
            if (provider.promptOverrides.hasAny()) {
                Spacer(Modifier.height(8.dp))
                MiniPill(stringResource(R.string.llm_provider_prompt_customized))
            }
            LlmDivider()
            val longStatus = test == ProviderTestState.Testing || test is ProviderTestState.Failed
            Row(verticalAlignment = Alignment.CenterVertically) {
                LlmTextAction(
                    text = stringResource(R.string.llm_manual_test),
                    onClick = { onTestProvider(provider.id) },
                )
                Spacer(Modifier.weight(1f))
                if (!longStatus) TestStatusText(test = test, provider = provider)
            }
            if (longStatus) {
                Spacer(Modifier.height(6.dp))
                TestStatusText(
                    test = test,
                    provider = provider,
                    onCancel = { onCancelTest(provider.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
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
    val defaultSubtitle = buildString {
        append(stringResource(R.string.llm_role_default_desc))
        if (stored.isBlank()) {
            selectedLlmEntry(state, role)?.label?.takeIf { it.isNotBlank() }?.let {
                append(" · ")
                append(it)
            }
        }
    }
    PikuBottomSheet(
        onDismissRequest = onDismiss,
        dark = dark,
        scrollable = true,
    ) {
        PikuSheetTitle(text = stringResource(R.string.llm_role_picker_title, roleLabel(role)))
        Spacer(Modifier.height(3.dp))
        PikuSheetSubtitle(text = roleDesc(role))
        Spacer(Modifier.height(14.dp))
        LlmOptionRow(
            title = stringResource(R.string.llm_role_default_option),
            subtitle = defaultSubtitle,
            selected = stored.isBlank(),
            onClick = onClear,
        )

        Spacer(Modifier.height(14.dp))
        OptionGroupLabel(stringResource(R.string.llm_role_group_builtin))
        if (builtin.isEmpty()) {
            Text(
                text = stringResource(R.string.llm_role_builtin_empty),
                color = PikuColors.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 2.dp, top = 6.dp, bottom = 6.dp),
            )
        } else {
            builtin.forEach { entry ->
                LlmOptionRow(
                    title = entry.label,
                    subtitle = entry.hint.ifBlank { entry.baseUrl },
                    selected = stored.isNotBlank() && selectedLlmEntry(state, role)?.id == entry.id,
                    onClick = { onSelectEntry(entry) },
                    badge = if (entry.free) {
                        { MiniPill(stringResource(R.string.llm_badge_free)) }
                    } else {
                        null
                    },
                )
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
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
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(start = 2.dp, top = 6.dp, bottom = 6.dp),
            )
            if (role != Role.IMAGE) {
                LlmTextAction(
                    text = stringResource(R.string.llm_manual_add),
                    onClick = onAddProvider,
                )
            }
        } else {
            manual.forEach { provider ->
                val entry = state.translateModels.firstOrNull { it.id == provider.id }
                if (entry != null) {
                    LlmOptionRow(
                        title = entry.label,
                        subtitle = entry.hint.ifBlank { entry.baseUrl },
                        selected = selectedLlmEntry(state, role)?.id == provider.id,
                        onClick = {
                            if (entry.isUsable()) onSelectEntry(entry) else onEditProvider(provider)
                        },
                        badge = if (!entry.isUsable()) {
                            { MiniPill(stringResource(R.string.llm_manual_key_missing), color = PikuColors.error) }
                        } else {
                            null
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            LlmTextAction(
                text = stringResource(R.string.llm_provider_cancel),
                onClick = onDismiss,
                color = PikuColors.textSecondary,
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun OptionGroupLabel(text: String) {
    Text(
        text = text,
        color = PikuColors.textFaint,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
    )
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
