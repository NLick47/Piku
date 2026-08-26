package com.piku.client.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.data.local.CatalogSource
import com.piku.client.data.local.SettingsRepository
import com.piku.client.R
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.ControlAccentDark
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.LoginCardBorderDark
import com.piku.client.ui.theme.LoginCardBorderLight
import com.piku.client.ui.theme.LoginTextFaintDark
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight

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

    var urlInput by rememberSaveable(state.catalogUrl) { mutableStateOf(state.catalogUrl) }
    var keyInput by rememberSaveable(state.catalogEncKey) { mutableStateOf(state.catalogEncKey) }
    var managedSource by remember { mutableStateOf<CatalogSource?>(null) }
    val refreshing = state.catalogRefreshState is CatalogRefreshState.Loading
    val trimmedKey = keyInput.trim()
    val keyValid = trimmedKey.isEmpty() || CATALOG_KEY_HEX.matches(trimmedKey)
    // 与当前持久化值都相同（地址空白=默认语义）时无需保存；刷新中或密钥格式非法时禁用
    val saveEnabled = !refreshing && keyValid &&
        (urlInput.trim() != state.catalogUrl || trimmedKey != state.catalogEncKey)
    // 两个输入框共用一套配色，避免逐字段重复
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AccentDark.copy(alpha = 0.8f),
        unfocusedBorderColor = if (dark) LoginCardBorderDark else LoginCardBorderLight,
        focusedContainerColor = if (dark) Color(0x14FFFFFF) else Color(0x0A000000),
        unfocusedContainerColor = if (dark) Color(0x14FFFFFF) else Color(0x0A000000),
        cursorColor = AccentDark,
        focusedTextColor = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
        unfocusedTextColor = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(if (dark) LoginBackgroundDark else Color.White),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = null,
                        tint = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.ai_translate_catalog_source),
                    color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            HorizontalDivider(
                color = if (dark) LoginCardBorderDark else LoginCardBorderLight,
                thickness = 0.5.dp,
            )
            Spacer(Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.ai_translate_catalog_desc),
            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(16.dp))

        // 已保存的源：官方默认固定首行 + 自定义源，点击即切换并刷新
        SheetSectionLabel(stringResource(R.string.ai_translate_catalog_saved), if (dark) LoginTextFaintDark else LoginTextFaintLight)
        Spacer(Modifier.height(8.dp))
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
                dark = dark,
            )
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text(stringResource(R.string.ai_translate_catalog_url_label)) },
            placeholder = {
                Text(
                    text = SettingsRepository.CATALOG_URL_DEFAULT,
                    color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            },
            singleLine = true,
            textStyle = TextStyle(fontSize = 12.sp, color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight),
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(14.dp))

        // 解密密钥：第三方加密目录的配套密钥（作者随地址一起分发）；明文 JSON 无需填写
        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = { Text(stringResource(R.string.ai_translate_catalog_key_label)) },
            placeholder = {
                Text(
                    text = stringResource(R.string.ai_translate_catalog_key_hint),
                    color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            },
            isError = !keyValid,
            supportingText = if (!keyValid) {
                { Text(stringResource(R.string.ai_translate_catalog_key_invalid), fontSize = 11.sp) }
            } else {
                null
            },
            singleLine = true,
            textStyle = TextStyle(fontSize = 12.sp, color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight),
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))

        // 存为新源：把当前编辑的地址+密钥入库（名称自动从 URL 推导，之后可重命名）
        TextButton(
            onClick = { onSaveAsSource(urlInput.trim(), trimmedKey) },
            enabled = !refreshing && keyValid && urlInput.isNotBlank(),
            contentPadding = PaddingValues(horizontal = 6.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = if (dark) ControlAccentDark else AccentDark),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(text = stringResource(R.string.ai_translate_catalog_save_as_new), fontSize = 12.sp)
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onResetCatalog,
                // 自定义地址或自定义密钥任一存在即可恢复默认（密钥随地址一并清除）
                enabled = !refreshing && (state.catalogIsCustom || state.catalogEncKey.isNotEmpty()),
                colors = ButtonDefaults.textButtonColors(contentColor = if (dark) LoginTextFaintDark else LoginTextFaintLight),
            ) {
                Text(text = stringResource(R.string.ai_translate_catalog_reset), fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onSaveCatalog(urlInput, keyInput) },
                enabled = saveEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentDark,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = stringResource(
                        if (refreshing) R.string.ai_translate_catalog_loading else R.string.ai_translate_catalog_save,
                    ),
                    fontSize = 13.sp,
                )
            }
        }

        when (val refresh = state.catalogRefreshState) {
            CatalogRefreshState.Idle -> Unit
            CatalogRefreshState.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 1.5.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.ai_translate_catalog_loading),
                    color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                    fontSize = 12.sp,
                )
            }
            is CatalogRefreshState.Success -> Text(
                text = stringResource(R.string.ai_translate_catalog_success, refresh.modelCount),
                color = if (dark) ControlAccentDark else AccentDark,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
            CatalogRefreshState.Failed -> Text(
                text = stringResource(R.string.ai_translate_catalog_failed),
                color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
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

/**
 * 已保存源列表卡片：与模型选择卡片同一套视觉（选中行 accent 淡底 + 右侧勾）。
 * 官方默认固定首行不可删；自定义源点行即切换，右侧 ⋮ 打开重命名/删除管理框。
 * 激活判定与实际生效的两键（地址+密钥）严格一致。
 */
@Composable
private fun CatalogSourceListCard(
    sources: List<CatalogSource>,
    activeUrl: String,
    activeKey: String,
    onActivateOfficial: () -> Unit,
    onActivateSource: (CatalogSource) -> Unit,
    onManageSource: (CatalogSource) -> Unit,
    dark: Boolean,
) {
    val primary = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
    val faint = if (dark) LoginTextFaintDark else LoginTextFaintLight
    val accent = if (dark) ControlAccentDark else AccentDark
    val borderColor = if (dark) LoginCardBorderDark else LoginCardBorderLight
    val officialActive = activeUrl == SettingsRepository.CATALOG_URL_DEFAULT && activeKey.isEmpty()

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(0.5.dp, borderColor), RoundedCornerShape(14.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (officialActive) accent.copy(alpha = 0.08f) else Color.Transparent)
                .clickable(enabled = !officialActive, onClick = onActivateOfficial)
                .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ai_translate_catalog_default),
                    color = primary,
                    fontSize = 14.sp,
                    fontWeight = if (officialActive) FontWeight.SemiBold else FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.ai_translate_catalog_official_caption),
                    color = faint,
                    fontSize = 11.sp,
                )
            }
            if (officialActive) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        sources.forEachIndexed { index, source ->
            HorizontalDivider(color = borderColor, thickness = 0.5.dp)
            val active = source.url == activeUrl && source.encKey == activeKey
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (active) accent.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable(enabled = !active, onClick = { onActivateSource(source) })
                    .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = source.name,
                        color = primary,
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = source.url,
                        color = faint,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                if (active) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                TextButton(
                    onClick = { onManageSource(source) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = faint),
                ) {
                    Text(text = "⋮", fontSize = 16.sp)
                }
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
    val primary = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
    var nameInput by rememberSaveable(source.id) { mutableStateOf(source.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.ai_translate_catalog_manage_title),
                color = primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text(stringResource(R.string.ai_translate_catalog_name_hint)) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(nameInput) }) {
                Text(text = stringResource(R.string.ai_translate_catalog_rename), fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text(text = stringResource(R.string.ai_translate_catalog_delete), fontSize = 13.sp)
            }
        },
    )
}
