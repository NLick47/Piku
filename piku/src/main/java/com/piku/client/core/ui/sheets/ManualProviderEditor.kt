package com.piku.client.core.ui.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.core.translation.AuthMode
import com.piku.client.core.translation.ManualProvider
import com.piku.client.core.translation.ManualProviderPreset
import com.piku.client.core.translation.PromptOverrides
import com.piku.client.core.translation.Role
import com.piku.client.core.ui.common.EffectOnChange
import com.piku.client.core.ui.common.PikuBottomSheet
import com.piku.client.core.ui.common.PikuPageHeader
import com.piku.client.core.ui.common.PikuSegmented
import com.piku.client.core.ui.common.PikuSheetTitle
import com.piku.client.core.ui.theme.HomeBgBottomDark
import com.piku.client.core.ui.theme.HomeBgBottomLight
import com.piku.client.core.ui.theme.HomeBgTopDark
import com.piku.client.core.ui.theme.HomeBgTopLight
import com.piku.client.core.ui.theme.PikuColors
import com.piku.client.source.poipiku.ui.home.ModelFetchState
import com.piku.client.source.poipiku.ui.home.ProviderTestState
import kotlinx.coroutines.launch

private enum class EditorError {
    URL_REQUIRED,
    URL_INVALID,
    MODEL_REQUIRED,
    KEY_REQUIRED,
    ROLES_REQUIRED,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ManualProviderEditorScreen(
    provider: ManualProvider,
    isNew: Boolean,
    initialPresetId: String?,
    testState: ProviderTestState?,
    modelFetchState: ModelFetchState,
    builtinTextPrompt: String,
    builtinNovelPrompt: String,
    builtinTextFromCatalog: Boolean,
    builtinNovelFromCatalog: Boolean,
    promptLangKey: String,
    onTest: (ManualProvider, String?) -> Unit,
    onFetchModels: (ManualProvider, String?) -> Unit,
    onClearModelFetch: () -> Unit,
    onSave: (ManualProvider, String?, Boolean) -> Unit,
    onRequestDelete: (ManualProvider) -> Unit,
    onDismiss: () -> Unit,
    onClearTest: () -> Unit,
    onCancelDraftTest: () -> Unit,
    dark: Boolean,
) {
    var selectedPresetId by remember(provider.id) {
        mutableStateOf(initialPresetId ?: ManualProviderPreset.CUSTOM_ID)
    }
    var name by remember(provider.id) { mutableStateOf(provider.name) }
    var baseUrl by remember(provider.id) { mutableStateOf(provider.baseUrl) }
    var model by remember(provider.id) { mutableStateOf(provider.model) }
    var apiKey by remember(provider.id) { mutableStateOf("") }
    var authMode by remember(provider.id) { mutableStateOf(provider.authMode.name) }
    var authHeader by remember(provider.id) { mutableStateOf(provider.authHeader) }
    var roleNames by remember(provider.id) {
        mutableStateOf(
            provider.roles.filter { it == Role.TEXT || it == Role.NOVEL }
                .ifEmpty { listOf(Role.TEXT) },
        )
    }
    var baseUrlsText by remember(provider.id) {
        mutableStateOf(provider.baseUrls.joinToString("\n"))
    }
    var extraHeadersText by remember(provider.id) {
        mutableStateOf(provider.extraHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }
    var advanced by remember(provider.id) {
        mutableStateOf(provider.baseUrls.isNotEmpty() || provider.extraHeaders.isNotEmpty())
    }
    var showKey by remember { mutableStateOf(false) }
    var clearKey by remember(provider.id) { mutableStateOf(false) }
    var error by remember { mutableStateOf<EditorError?>(null) }
    var showModelPicker by remember { mutableStateOf(false) }
    var promptOverrides by remember(provider.id) { mutableStateOf(provider.promptOverrides) }
    var showPromptEditor by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val mode = runCatching { AuthMode.valueOf(authMode) }.getOrDefault(AuthMode.NONE)
    val preset = ManualProviderPreset.find(selectedPresetId)
    val secondary = PikuColors.textSecondary
    val errorColor = PikuColors.error
    val scope = rememberCoroutineScope()
    val urlRequester = remember { BringIntoViewRequester() }
    val keyRequester = remember { BringIntoViewRequester() }
    val modelRequester = remember { BringIntoViewRequester() }
    val rolesRequester = remember { BringIntoViewRequester() }
    val scrollState = rememberScrollState()

    val dirty = name != provider.name ||
        baseUrl != provider.baseUrl ||
        model != provider.model ||
        apiKey.isNotBlank() ||
        clearKey ||
        authMode != provider.authMode.name ||
        authHeader != provider.authHeader ||
        roleNames != provider.roles.filter { it == Role.TEXT || it == Role.NOVEL }
            .ifEmpty { listOf(Role.TEXT) } ||
        baseUrlsText != provider.baseUrls.joinToString("\n") ||
        extraHeadersText != provider.extraHeaders.entries
            .joinToString("\n") { "${it.key}: ${it.value}" } ||
        promptOverrides != provider.promptOverrides

    fun requestDismiss() {
        if (dirty) showDiscardConfirm = true else onDismiss()
    }

    BackHandler(enabled = true) { requestDismiss() }

    EffectOnChange({ advanced }) {
        if (advanced) {
            withFrameNanos { }
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    fun showError(err: EditorError?) {
        error = err
        if (err != null) scope.launch {
            when (err) {
                EditorError.URL_REQUIRED, EditorError.URL_INVALID -> urlRequester.bringIntoView()
                EditorError.KEY_REQUIRED -> keyRequester.bringIntoView()
                EditorError.MODEL_REQUIRED -> modelRequester.bringIntoView()
                EditorError.ROLES_REQUIRED -> rolesRequester.bringIntoView()
            }
        }
    }

    fun applyPreset(target: ManualProviderPreset?) {
        selectedPresetId = target?.id ?: ManualProviderPreset.CUSTOM_ID
        name = target?.name.orEmpty()
        baseUrl = target?.baseUrl.orEmpty()
        model = target?.defaultModel.orEmpty()
        authMode = (target?.authMode ?: AuthMode.NONE).name
        roleNames = target?.roles
            ?.filter { it == Role.TEXT || it == Role.NOVEL }
            ?.ifEmpty { listOf(Role.TEXT) }
            ?: listOf(Role.TEXT)
        apiKey = ""
        clearKey = false
        error = null
        advanced = false
        baseUrlsText = ""
        extraHeadersText = ""
        promptOverrides = PromptOverrides()
        onClearTest()
        onClearModelFetch()
    }

    fun currentProvider(): ManualProvider = provider.copy(
        id = provider.id,
        name = name.trim(),
        baseUrl = baseUrl.trim(),
        model = model.trim(),
        authMode = mode,
        authHeader = authHeader.trim().ifBlank { ManualProvider.DEFAULT_AUTH_HEADER },
        roles = roleNames,
        baseUrls = baseUrlsText.lines().map { it.trim() }.filter { it.isNotBlank() },
        extraHeaders = parseHeaderLines(extraHeadersText),
        promptOverrides = promptOverrides.normalized(),
        hasKey = provider.hasKey,
    )

    fun validate(requireModel: Boolean, requireRoles: Boolean): EditorError? {
        val url = baseUrl.trim()
        if (url.isBlank()) return EditorError.URL_REQUIRED
        if (!url.startsWith("http://") && !url.startsWith("https://")) return EditorError.URL_INVALID
        if (requireModel && model.isBlank()) return EditorError.MODEL_REQUIRED
        if (mode != AuthMode.NONE) {
            val keepExisting = !isNew && provider.hasKey && !clearKey
            if (apiKey.isBlank() && !keepExisting) return EditorError.KEY_REQUIRED
        }
        if (requireRoles && roleNames.isEmpty()) return EditorError.ROLES_REQUIRED
        return null
    }

    fun keyArg(): String? = apiKey.takeIf { it.isNotBlank() && mode != AuthMode.NONE }

    LaunchedEffect(modelFetchState) {
        if (modelFetchState is ModelFetchState.Success) showModelPicker = true
    }

    if (showPromptEditor) {
        ProviderPromptEditor(
            overrides = promptOverrides,
            defaultText = builtinTextPrompt,
            defaultNovel = builtinNovelPrompt,
            textFromCatalog = builtinTextFromCatalog,
            novelFromCatalog = builtinNovelFromCatalog,
            langKey = promptLangKey,
            onChange = { promptOverrides = it },
            onBack = { showPromptEditor = false },
            dark = dark,
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (dark) listOf(HomeBgTopDark, HomeBgBottomDark)
                    else listOf(HomeBgTopLight, HomeBgBottomLight),
                ),
            )
            .navigationBarsPadding()
            .imePadding(),
    ) {
        PikuPageHeader(
            title = stringResource(
                if (isNew) R.string.llm_provider_add_title else R.string.llm_provider_edit_title,
            ),
            subtitle = stringResource(R.string.llm_provider_page_subtitle),
            dark = dark,
            onBack = { requestDismiss() },
            trailing = if (isNew) {
                null
            } else {
                {
                    LlmTextAction(
                        text = stringResource(R.string.llm_manual_delete),
                        onClick = { onRequestDelete(provider) },
                        color = errorColor,
                    )
                }
            },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 16.dp),
        ) {
            if (isNew) {
                SettingsSectionLabel(stringResource(R.string.llm_provider_section_preset))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ManualProviderPreset.QUICK_ADD.forEach { item ->
                        LlmChip(
                            text = item.name,
                            selected = selectedPresetId == item.id,
                            onClick = {
                                if (selectedPresetId != item.id) applyPreset(item)
                            },
                        )
                    }
                    LlmChip(
                        text = stringResource(R.string.llm_provider_preset_custom),
                        selected = selectedPresetId == ManualProviderPreset.CUSTOM_ID,
                        onClick = {
                            if (selectedPresetId != ManualProviderPreset.CUSTOM_ID) applyPreset(null)
                        },
                    )
                }
                Spacer(Modifier.height(18.dp))
            }

            SettingsSectionLabel(stringResource(R.string.llm_provider_section_connection))
            SettingsCard(dark = dark) {
                Column(Modifier.padding(14.dp)) {
                    LlmTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = stringResource(R.string.llm_provider_name),
                        placeholder = stringResource(R.string.llm_provider_name_hint),
                    )
                    Spacer(Modifier.height(14.dp))

                    LlmTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it; error = null },
                        label = stringResource(R.string.llm_provider_base_url),
                        placeholder = preset?.baseUrlPlaceholder ?: "http://192.168.1.10:8000/v1",
                        mono = true,
                        error = error == EditorError.URL_REQUIRED || error == EditorError.URL_INVALID,
                        requester = urlRequester,
                    )
                    FieldError(
                        when (error) {
                            EditorError.URL_REQUIRED -> stringResource(R.string.llm_provider_url_required)
                            EditorError.URL_INVALID -> stringResource(R.string.llm_provider_url_invalid)
                            else -> null
                        },
                    )
                    Text(
                        text = stringResource(R.string.llm_provider_base_url_hint),
                        color = secondary,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                    )
                    Spacer(Modifier.height(14.dp))

                    LlmTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            clearKey = false
                            error = null
                        },
                        label = stringResource(R.string.llm_provider_api_key),
                        placeholder = when {
                            mode == AuthMode.NONE -> null
                            !isNew && provider.hasKey && !clearKey ->
                                stringResource(R.string.llm_manual_key_keep_hint)
                            else -> "sk-..."
                        },
                        enabled = mode != AuthMode.NONE,
                        error = error == EditorError.KEY_REQUIRED,
                        visualTransformation = if (showKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailing = if (mode != AuthMode.NONE) {
                            {
                                LlmTextAction(
                                    text = stringResource(
                                        if (showKey) R.string.password_hide else R.string.password_show,
                                    ),
                                    onClick = { showKey = !showKey },
                                    color = secondary,
                                )
                            }
                        } else {
                            null
                        },
                        requester = keyRequester,
                    )
                    FieldError(
                        if (error == EditorError.KEY_REQUIRED) {
                            stringResource(R.string.llm_provider_key_required)
                        } else {
                            null
                        },
                    )
                    Text(
                        text = when {
                            clearKey -> stringResource(R.string.llm_manual_key_will_clear)
                            mode == AuthMode.NONE -> stringResource(R.string.llm_manual_key_placeholder)
                            !isNew && provider.hasKey -> stringResource(R.string.llm_manual_key_keep_hint)
                            else -> stringResource(R.string.llm_manual_key_saved_hint)
                        },
                        color = if (clearKey) errorColor else secondary,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                    )
                    if (!isNew && provider.hasKey && !clearKey) {
                        Spacer(Modifier.height(4.dp))
                        LlmTextAction(
                            text = stringResource(R.string.llm_manual_key_clear),
                            onClick = {
                                clearKey = true
                                apiKey = ""
                                error = null
                            },
                            color = secondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            SettingsSectionLabel(stringResource(R.string.llm_provider_section_model))
            SettingsCard(dark = dark) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        LlmTextField(
                            value = model,
                            onValueChange = { model = it; error = null },
                            label = stringResource(R.string.llm_provider_model),
                            placeholder = stringResource(R.string.llm_provider_model_hint),
                            error = error == EditorError.MODEL_REQUIRED,
                            modifier = Modifier.weight(1f),
                            requester = modelRequester,
                        )
                        Spacer(Modifier.width(10.dp))
                        LlmActionButton(
                            text = stringResource(R.string.llm_provider_fetch_models),
                            primary = false,
                            height = 40.dp,
                            loading = modelFetchState == ModelFetchState.Loading,
                            onClick = {
                                val err = validate(requireModel = false, requireRoles = false)
                                showError(err)
                                if (err == null) onFetchModels(currentProvider(), keyArg())
                            },
                            modifier = Modifier.width(88.dp),
                        )
                    }
                    FieldError(
                        if (error == EditorError.MODEL_REQUIRED) {
                            stringResource(R.string.llm_provider_model_required)
                        } else {
                            null
                        },
                    )
                    Text(
                        text = when (val fetch = modelFetchState) {
                            is ModelFetchState.Failed -> stringResource(
                                R.string.llm_provider_fetch_failed,
                                fetch.message.orEmpty(),
                            )
                            else -> stringResource(R.string.llm_provider_fetch_models_hint)
                        },
                        color = if (modelFetchState is ModelFetchState.Failed) errorColor else secondary,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                    )
                    Spacer(Modifier.height(16.dp))

                    FieldLabel(stringResource(R.string.llm_provider_roles))
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.bringIntoViewRequester(rolesRequester),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(Role.TEXT, Role.NOVEL).forEach { role ->
                            val selected = role in roleNames
                            LlmChip(
                                text = roleLabel(role),
                                selected = selected,
                                onClick = {
                                    roleNames = if (selected) roleNames - role else roleNames + role
                                    error = null
                                },
                            )
                        }
                    }
                    FieldError(
                        if (error == EditorError.ROLES_REQUIRED) {
                            stringResource(R.string.llm_provider_roles_required)
                        } else {
                            null
                        },
                    )
                    Text(
                        text = stringResource(R.string.llm_provider_roles_hint),
                        color = PikuColors.textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            SettingsSectionLabel(stringResource(R.string.llm_provider_auth))
            SettingsCard(dark = dark) {
                Column(Modifier.padding(12.dp)) {
                    PikuSegmented(
                        labels = listOf(
                            stringResource(R.string.llm_manual_no_auth),
                            stringResource(R.string.llm_manual_bearer),
                            stringResource(R.string.llm_manual_header),
                        ),
                        selectedIndex = when (mode) {
                            AuthMode.NONE -> 0
                            AuthMode.BEARER -> 1
                            AuthMode.HEADER -> 2
                        },
                        onSelect = { index ->
                            authMode = when (index) {
                                0 -> AuthMode.NONE.name
                                1 -> AuthMode.BEARER.name
                                else -> AuthMode.HEADER.name
                            }
                            error = null
                        },
                    )
                    if (mode == AuthMode.HEADER) {
                        Spacer(Modifier.height(14.dp))
                        LlmTextField(
                            value = authHeader,
                            onValueChange = { authHeader = it },
                            label = stringResource(R.string.llm_provider_header_name),
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            SettingsSectionLabel(stringResource(R.string.llm_provider_prompt_section))
            SettingsCard(dark = dark) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    LlmEntryRow(
                        title = stringResource(R.string.llm_prompt_settings),
                        subtitle = stringResource(
                            if (promptOverrides.hasAny(promptLangKey)) {
                                R.string.llm_provider_prompt_customized_desc
                            } else {
                                R.string.llm_provider_prompt_desc
                            },
                        ),
                        onClick = { showPromptEditor = true },
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            SettingsCard(dark = dark) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { advanced = !advanced }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.llm_provider_advanced),
                        color = secondary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    LlmChevron(
                        Modifier.graphicsLayer(rotationZ = if (advanced) 270f else 90f),
                    )
                }
                if (advanced) {
                    LlmDivider()
                    Column(
                        Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    ) {
                        LlmTextField(
                            value = baseUrlsText,
                            onValueChange = { baseUrlsText = it },
                            label = stringResource(R.string.llm_provider_base_urls),
                            placeholder = stringResource(R.string.llm_provider_base_urls_hint),
                            singleLine = false,
                            minLines = 2,
                        )
                        Spacer(Modifier.height(14.dp))
                        LlmTextField(
                            value = extraHeadersText,
                            onValueChange = { extraHeadersText = it },
                            label = stringResource(R.string.llm_provider_headers),
                            placeholder = stringResource(R.string.llm_provider_headers_hint),
                            singleLine = false,
                            minLines = 2,
                        )
                    }
                }
            }
        }

        SettingsFooter(dark = dark) {
            val errorSummary = editorErrorText(error)
            if (errorSummary != null) {
                Text(
                    text = errorSummary,
                    color = errorColor,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            DraftTestLine(testState = testState, onCancel = onCancelDraftTest)
            Spacer(Modifier.height(9.dp))
            Row {
                LlmActionButton(
                    text = stringResource(R.string.llm_provider_test),
                    primary = false,
                    onClick = {
                        val err = validate(requireModel = true, requireRoles = false)
                        showError(err)
                        if (err == null) onTest(currentProvider(), keyArg())
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                LlmActionButton(
                    text = stringResource(R.string.llm_provider_save),
                    primary = true,
                    onClick = {
                        val err = validate(requireModel = true, requireRoles = true)
                        showError(err)
                        if (err == null) {
                            onSave(
                                currentProvider(),
                                keyArg(),
                                clearKey || mode == AuthMode.NONE,
                            )
                        }
                    },
                    modifier = Modifier.weight(1.15f),
                )
            }
        }
    }

    if (showModelPicker && modelFetchState is ModelFetchState.Success) {
        ModelPickerSheet(
            models = modelFetchState.models,
            selected = model,
            dark = dark,
            onSelect = {
                model = it
                error = null
                showModelPicker = false
                onClearModelFetch()
            },
            onDismiss = {
                showModelPicker = false
                onClearModelFetch()
            },
        )
    }

    if (showDiscardConfirm) {
        ConfirmDestructiveDialog(
            title = stringResource(R.string.llm_discard_title),
            message = stringResource(R.string.llm_discard_message),
            confirmLabel = stringResource(R.string.llm_discard_confirm),
            onConfirm = {
                showDiscardConfirm = false
                onDismiss()
            },
            onDismiss = { showDiscardConfirm = false },
            dark = dark,
        )
    }
}

@Composable
private fun editorErrorText(error: EditorError?): String? {
    val res = when (error) {
        EditorError.URL_REQUIRED -> R.string.llm_provider_url_required
        EditorError.URL_INVALID -> R.string.llm_provider_url_invalid
        EditorError.MODEL_REQUIRED -> R.string.llm_provider_model_required
        EditorError.KEY_REQUIRED -> R.string.llm_provider_key_required
        EditorError.ROLES_REQUIRED -> R.string.llm_provider_roles_required
        else -> return null
    }
    return stringResource(res)
}

@Composable
private fun DraftTestLine(testState: ProviderTestState?, onCancel: () -> Unit) {
    val secondary = PikuColors.textSecondary
    when (testState) {
        ProviderTestState.Testing -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                color = secondary,
                strokeWidth = 1.4.dp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.llm_manual_testing),
                color = secondary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            LlmTextAction(
                text = stringResource(R.string.llm_provider_cancel),
                onClick = onCancel,
                color = secondary,
            )
        }
        is ProviderTestState.Success -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = secondary,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.llm_manual_connected_latency, testState.latencyMs),
                color = secondary,
                fontSize = 11.sp,
            )
        }
        is ProviderTestState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = null,
                tint = PikuColors.error,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (testState.message.isNullOrBlank()) {
                    stringResource(R.string.llm_provider_key_required)
                } else {
                    stringResource(R.string.llm_manual_failed_detail, testState.message)
                },
                color = PikuColors.error,
                fontSize = 11.sp,
                maxLines = 2,
            )
        }
        else -> Text(
            text = stringResource(R.string.llm_provider_test_hint),
            color = PikuColors.textSecondary,
            fontSize = 11.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    models: List<String>,
    selected: String,
    dark: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(models, query) {
        if (query.isBlank()) models else models.filter { it.contains(query, ignoreCase = true) }
    }
    PikuBottomSheet(
        onDismissRequest = onDismiss,
        dark = dark,
    ) {
        PikuSheetTitle(text = stringResource(R.string.llm_provider_model_picker))
        Spacer(Modifier.height(10.dp))
        LlmTextField(
            value = query,
            onValueChange = { query = it },
            label = null,
            placeholder = stringResource(R.string.llm_provider_model_picker_search),
        )
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            Text(
                text = stringResource(R.string.llm_provider_model_picker_empty),
                color = PikuColors.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                items(filtered, key = { it }) { id ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(id) }
                            .padding(horizontal = 4.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = id,
                            color = PikuColors.textPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (id == selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = PikuColors.textPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            LlmTextAction(
                text = stringResource(R.string.llm_provider_cancel),
                onClick = onDismiss,
                color = PikuColors.textSecondary,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun parseHeaderLines(raw: String): Map<String, String> =
    raw.lines()
        .mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) null
            else line.substring(0, colon).trim() to line.substring(colon + 1).trim()
        }
        .filter { it.first.isNotBlank() }
        .toMap()
