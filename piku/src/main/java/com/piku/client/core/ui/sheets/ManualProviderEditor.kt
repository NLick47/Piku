package com.piku.client.core.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.piku.client.core.ui.common.PikuBottomSheet
import com.piku.client.core.ui.common.PikuPageHeader
import com.piku.client.core.ui.common.PikuSegmented
import com.piku.client.core.ui.theme.HomeBgBottomDark
import com.piku.client.core.ui.theme.HomeBgBottomLight
import com.piku.client.core.ui.theme.HomeBgTopDark
import com.piku.client.core.ui.theme.HomeBgTopLight
import com.piku.client.core.ui.theme.PikuColors
import com.piku.client.core.ui.theme.onAccent
import com.piku.client.source.poipiku.ui.home.ModelFetchState
import com.piku.client.source.poipiku.ui.home.ProviderTestState

private enum class EditorError {
    URL_REQUIRED,
    URL_INVALID,
    MODEL_REQUIRED,
    KEY_REQUIRED,
    ROLES_REQUIRED,
}

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

    val mode = runCatching { AuthMode.valueOf(authMode) }.getOrDefault(AuthMode.NONE)
    val preset = ManualProviderPreset.find(selectedPresetId)
    val primary = PikuColors.textPrimary
    val secondary = PikuColors.textSecondary
    val accent = PikuColors.controlAccent
    val errorColor = PikuColors.error
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent,
        unfocusedBorderColor = PikuColors.border,
        errorBorderColor = errorColor,
        focusedContainerColor = if (dark) Color(0x14FFFFFF) else Color(0x0A000000),
        unfocusedContainerColor = if (dark) Color(0x14FFFFFF) else Color(0x0A000000),
        errorContainerColor = if (dark) Color(0x14FFFFFF) else Color(0x0A000000),
        disabledBorderColor = Color.Transparent,
        disabledContainerColor = if (dark) Color(0x14FFFFFF) else Color(0x0A000000),
        disabledTextColor = secondary,
        disabledLabelColor = secondary,
        disabledPlaceholderColor = secondary,
        disabledTrailingIconColor = secondary,
        cursorColor = accent,
        errorCursorColor = errorColor,
        focusedTextColor = primary,
        unfocusedTextColor = primary,
        focusedLabelColor = accent,
        unfocusedLabelColor = secondary,
        errorLabelColor = errorColor,
        focusedPlaceholderColor = secondary,
        unfocusedPlaceholderColor = secondary,
        focusedSupportingTextColor = errorColor,
        unfocusedSupportingTextColor = secondary,
        errorSupportingTextColor = errorColor,
    )

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
            onBack = onDismiss,
            trailing = if (isNew) {
                null
            } else {
                {
                    TextButton(
                        onClick = { onRequestDelete(provider) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.llm_manual_delete),
                            color = errorColor,
                            fontSize = 12.sp,
                        )
                    }
                }
            },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 16.dp),
        ) {
            if (isNew) {
                SettingsSectionLabel(stringResource(R.string.llm_provider_section_preset))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ManualProviderPreset.QUICK_ADD.forEach { item ->
                        PresetChip(
                            text = item.name,
                            selected = selectedPresetId == item.id,
                            onClick = {
                                if (selectedPresetId != item.id) applyPreset(item)
                            },
                        )
                    }
                    PresetChip(
                        text = stringResource(R.string.llm_provider_preset_custom),
                        selected = selectedPresetId == ManualProviderPreset.CUSTOM_ID,
                        onClick = {
                            if (selectedPresetId != ManualProviderPreset.CUSTOM_ID) {
                                applyPreset(null)
                            }
                        },
                    )
                }
                Spacer(Modifier.height(18.dp))
            }

            SettingsSectionLabel(stringResource(R.string.llm_provider_section_connection))
            SettingsCard(dark = dark) {
                Column(Modifier.padding(14.dp)) {
                    FieldLabel(stringResource(R.string.llm_provider_name))
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text(stringResource(R.string.llm_provider_name_hint)) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = primary),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))

                    FieldLabel(stringResource(R.string.llm_provider_base_url))
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it; error = null },
                        placeholder = {
                            Text(preset?.baseUrlPlaceholder ?: "http://192.168.1.10:8000/v1")
                        },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = primary),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
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
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(start = 2.dp, top = 5.dp),
                    )
                    Spacer(Modifier.height(12.dp))

                    FieldLabel(stringResource(R.string.llm_provider_api_key))
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            clearKey = false
                            error = null
                        },
                        placeholder = {
                            Text(
                                when {
                                    mode == AuthMode.NONE ->
                                        stringResource(R.string.llm_manual_key_placeholder)
                                    !isNew && provider.hasKey && !clearKey ->
                                        stringResource(R.string.llm_manual_key_keep_hint)
                                    else -> "sk-..."
                                },
                            )
                        },
                        singleLine = true,
                        enabled = mode != AuthMode.NONE,
                        visualTransformation = if (showKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            if (mode != AuthMode.NONE) {
                                TextButton(
                                    onClick = { showKey = !showKey },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (showKey) R.string.password_hide else R.string.password_show,
                                        ),
                                        color = secondary,
                                        fontSize = 10.5.sp,
                                    )
                                }
                            }
                        },
                        textStyle = TextStyle(fontSize = 13.sp, color = primary),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
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
                            !isNew && provider.hasKey ->
                                stringResource(R.string.llm_manual_key_keep_hint)
                            else -> stringResource(R.string.llm_manual_key_saved_hint)
                        },
                        color = if (clearKey) errorColor else secondary,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(start = 2.dp, top = 5.dp),
                    )
                    if (!isNew && provider.hasKey && !clearKey) {
                        TextButton(
                            onClick = {
                                clearKey = true
                                apiKey = ""
                                error = null
                            },
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.llm_manual_key_clear),
                                color = secondary,
                                fontSize = 10.5.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            SettingsSectionLabel(stringResource(R.string.llm_provider_section_model))
            SettingsCard(dark = dark) {
                Column(Modifier.padding(14.dp)) {
                    FieldLabel(stringResource(R.string.llm_provider_model))
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it; error = null },
                            placeholder = { Text(stringResource(R.string.llm_provider_model_hint)) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp, color = primary),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                val err = validate(requireModel = false, requireRoles = false)
                                error = err
                                if (err == null) {
                                    onFetchModels(currentProvider(), keyArg())
                                }
                            },
                            enabled = modelFetchState != ModelFetchState.Loading,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(48.dp),
                        ) {
                            if (modelFetchState == ModelFetchState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = accent,
                                    strokeWidth = 1.5.dp,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.llm_provider_fetch_models),
                                    color = accent,
                                    fontSize = 11.5.sp,
                                )
                            }
                        }
                    }
                    FieldError(
                        when (error) {
                            EditorError.MODEL_REQUIRED -> stringResource(R.string.llm_provider_model_required)
                            else -> null
                        },
                    )
                    when (val fetch = modelFetchState) {
                        is ModelFetchState.Failed -> Text(
                            text = stringResource(
                                R.string.llm_provider_fetch_failed,
                                fetch.message.orEmpty(),
                            ),
                            color = errorColor,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(start = 2.dp, top = 5.dp),
                        )
                        else -> Text(
                            text = stringResource(R.string.llm_provider_fetch_models_hint),
                            color = secondary,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(start = 2.dp, top = 5.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    FieldLabel(stringResource(R.string.llm_provider_roles))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Role.TEXT, Role.NOVEL).forEach { role ->
                            val selected = role in roleNames
                            RoleToggleChip(
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
                        color = PikuColors.textFaint,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(start = 2.dp, top = 5.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
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
                        Spacer(Modifier.height(12.dp))
                        FieldLabel(stringResource(R.string.llm_provider_header_name))
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = authHeader,
                            onValueChange = { authHeader = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp, color = primary),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            SettingsSectionLabel(stringResource(R.string.llm_provider_prompt_section))
            SettingsCard(dark = dark) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showPromptEditor = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.llm_prompt_settings),
                            color = primary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(
                                if (promptOverrides.hasAny(promptLangKey)) {
                                    R.string.llm_provider_prompt_customized_desc
                                } else {
                                    R.string.llm_provider_prompt_desc
                                },
                            ),
                            color = secondary,
                            fontSize = 10.5.sp,
                        )
                    }
                    Text(
                        text = "›",
                        color = PikuColors.textFaint,
                        fontSize = 18.sp,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SettingsCard(dark = dark) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { advanced = !advanced }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.llm_provider_advanced),
                        color = secondary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (advanced) "▾" else "▸",
                        color = PikuColors.textFaint,
                        fontSize = 12.sp,
                    )
                }
                if (advanced) {
                    HorizontalDivider(
                        color = PikuColors.border,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                    Column(Modifier.padding(14.dp)) {
                        FieldLabel(stringResource(R.string.llm_provider_base_urls))
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = baseUrlsText,
                            onValueChange = { baseUrlsText = it },
                            placeholder = { Text(stringResource(R.string.llm_provider_base_urls_hint)) },
                            minLines = 2,
                            maxLines = 4,
                            textStyle = TextStyle(fontSize = 12.5.sp, color = primary),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        FieldLabel(stringResource(R.string.llm_provider_headers))
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = extraHeadersText,
                            onValueChange = { extraHeadersText = it },
                            placeholder = { Text(stringResource(R.string.llm_provider_headers_hint)) },
                            minLines = 2,
                            maxLines = 4,
                            textStyle = TextStyle(fontSize = 12.5.sp, color = primary),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        SettingsFooter(dark = dark) {
            DraftTestLine(testState = testState)
            Spacer(Modifier.height(7.dp))
            Row {
                OutlinedButton(
                    onClick = {
                        val err = validate(requireModel = true, requireRoles = false)
                        error = err
                        if (err == null) onTest(currentProvider(), keyArg())
                    },
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text(stringResource(R.string.llm_provider_test), fontSize = 12.5.sp)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val err = validate(requireModel = true, requireRoles = true)
                        error = err
                        if (err == null) {
                            onSave(
                                currentProvider(),
                                keyArg(),
                                clearKey || mode == AuthMode.NONE,
                            )
                        }
                    },
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = onAccent(),
                    ),
                    modifier = Modifier.weight(1.15f).height(48.dp),
                ) {
                    Text(stringResource(R.string.llm_provider_save), fontSize = 13.sp)
                }
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
}

@Composable
private fun DraftTestLine(testState: ProviderTestState?) {
    when (testState) {
        ProviderTestState.Testing -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                color = PikuColors.controlAccent,
                strokeWidth = 1.4.dp,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = stringResource(R.string.llm_manual_testing),
                color = PikuColors.textSecondary,
                fontSize = 10.5.sp,
            )
        }
        is ProviderTestState.Success -> Text(
            text = stringResource(R.string.llm_manual_connected_latency, testState.latencyMs),
            color = Color(0xFF5BAE61),
            fontSize = 10.5.sp,
        )
        is ProviderTestState.Failed -> Text(
            text = if (testState.message.isNullOrBlank()) {
                stringResource(R.string.llm_provider_key_required)
            } else {
                stringResource(R.string.llm_manual_failed_detail, testState.message)
            },
            color = PikuColors.error,
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        else -> Text(
            text = stringResource(R.string.llm_provider_test_hint),
            color = PikuColors.textFaint,
            fontSize = 10.5.sp,
        )
    }
}

@Composable
private fun PresetChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) PikuColors.textPrimary else PikuColors.textSecondary,
        fontSize = 11.5.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) PikuColors.controlAccent.copy(alpha = 0.12f)
                else PikuColors.surface,
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (selected) PikuColors.controlAccent.copy(alpha = 0.25f)
                    else PikuColors.border,
                ),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    )
}

@Composable
private fun RoleToggleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) PikuColors.textPrimary else PikuColors.textSecondary,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) PikuColors.controlAccent.copy(alpha = 0.14f) else PikuColors.surfaceMuted,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
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
        if (query.isBlank()) {
            models
        } else {
            models.filter { it.contains(query, ignoreCase = true) }
        }
    }
    PikuBottomSheet(
        onDismissRequest = onDismiss,
        dark = dark,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.llm_provider_model_picker),
                color = PikuColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.llm_provider_model_picker_count, models.size),
                color = PikuColors.textFaint,
                fontSize = 10.5.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = {
                Text(
                    text = stringResource(R.string.llm_provider_model_picker_search),
                    fontSize = 12.sp,
                )
            },
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp, color = PikuColors.textPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            Text(
                text = stringResource(R.string.llm_provider_model_picker_empty),
                color = PikuColors.textSecondary,
                fontSize = 11.5.sp,
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
                            fontSize = 12.5.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        if (id == selected) {
                            Text(
                                text = stringResource(R.string.llm_provider_model_picker_current),
                                color = PikuColors.controlAccent,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
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
