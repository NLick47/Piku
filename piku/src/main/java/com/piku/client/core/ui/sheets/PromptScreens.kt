package com.piku.client.core.ui.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.core.translation.PromptOverrides
import com.piku.client.core.translation.Role
import com.piku.client.core.ui.common.PikuPageHeader
import com.piku.client.core.ui.common.PikuSegmented
import com.piku.client.core.ui.theme.HomeBgBottomDark
import com.piku.client.core.ui.theme.HomeBgBottomLight
import com.piku.client.core.ui.theme.HomeBgTopDark
import com.piku.client.core.ui.theme.HomeBgTopLight
import com.piku.client.core.ui.theme.PikuColors
import com.piku.client.core.ui.theme.onAccent
import com.piku.client.source.poipiku.ui.home.HomeUiState

@Composable
internal fun BuiltinPromptScreen(
    state: HomeUiState,
    dark: Boolean,
    onBack: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val role = if (tab == 0) Role.TEXT else Role.NOVEL
    val text = if (tab == 0) state.builtinTextPrompt else state.builtinNovelPrompt
    val fromCatalog = if (tab == 0) {
        state.builtinTextFromCatalog
    } else {
        state.builtinNovelFromCatalog
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(promptPageBackground(dark))
            .navigationBarsPadding(),
    ) {
        PikuPageHeader(
            title = stringResource(R.string.llm_builtin_prompt_title),
            subtitle = stringResource(R.string.llm_builtin_prompt_desc),
            dark = dark,
            onBack = onBack,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 18.dp),
        ) {
            PromptTabs(tab = tab, onSelect = { tab = it })
            PromptLanguageLine(langKey = state.promptLangKey)
            PromptNote(stringResource(R.string.llm_builtin_prompt_note))
            PromptCard(
                role = role,
                text = text,
                defaultText = text,
                readOnly = true,
                fromCatalog = fromCatalog,
                modified = false,
                onTextChange = {},
                onReset = {},
                dark = dark,
            )
            Text(
                text = stringResource(R.string.llm_prompt_novel_note),
                color = PikuColors.textFaint,
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 4.dp, top = 14.dp),
            )
        }
    }
}

@Composable
internal fun ProviderPromptEditor(
    overrides: PromptOverrides,
    defaultText: String,
    defaultNovel: String,
    textFromCatalog: Boolean,
    novelFromCatalog: Boolean,
    langKey: String,
    onChange: (PromptOverrides) -> Unit,
    onBack: () -> Unit,
    dark: Boolean,
) {
    var tab by remember { mutableIntStateOf(0) }
    var textDraft by remember(overrides, defaultText) {
        mutableStateOf(overrides.value(Role.TEXT, langKey).ifBlank { defaultText })
    }
    var novelDraft by remember(overrides, defaultNovel) {
        mutableStateOf(overrides.value(Role.NOVEL, langKey).ifBlank { defaultNovel })
    }

    val role = if (tab == 0) Role.TEXT else Role.NOVEL
    val draft = if (tab == 0) textDraft else novelDraft
    val default = if (tab == 0) defaultText else defaultNovel
    val fromCatalog = if (tab == 0) textFromCatalog else novelFromCatalog
    val modified = draft.trim() != default.trim()

    fun applyAndBack() {
        val next = overrides
            .withValue(Role.TEXT, langKey, textDraft, defaultText)
            .withValue(Role.NOVEL, langKey, novelDraft, defaultNovel)
        onChange(next)
        onBack()
    }

    BackHandler { applyAndBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(promptPageBackground(dark))
            .navigationBarsPadding()
            .imePadding(),
    ) {
        PikuPageHeader(
            title = stringResource(R.string.llm_prompt_settings),
            subtitle = stringResource(R.string.llm_provider_prompt_subtitle),
            dark = dark,
            onBack = { applyAndBack() },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 18.dp),
        ) {
            PromptTabs(tab = tab, onSelect = { tab = it })
            PromptLanguageLine(langKey = langKey)
            PromptNote(stringResource(R.string.llm_provider_prompt_note))
            PromptCard(
                role = role,
                text = draft,
                defaultText = default,
                readOnly = false,
                fromCatalog = fromCatalog,
                modified = modified,
                onTextChange = {
                    if (tab == 0) textDraft = it else novelDraft = it
                },
                onReset = {
                    if (tab == 0) textDraft = defaultText else novelDraft = defaultNovel
                },
                dark = dark,
            )
            Text(
                text = stringResource(R.string.llm_prompt_novel_note),
                color = PikuColors.textFaint,
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 4.dp, top = 14.dp),
            )
        }
        SettingsFooter(dark = dark) {
            Text(
                text = stringResource(R.string.llm_prompt_apply_hint),
                color = PikuColors.textSecondary,
                fontSize = 10.5.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 7.dp),
            )
            Button(
                onClick = { applyAndBack() },
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PikuColors.controlAccent,
                    contentColor = onAccent(),
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(R.string.llm_prompt_done), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PromptTabs(tab: Int, onSelect: (Int) -> Unit) {
    PikuSegmented(
        labels = listOf(
            stringResource(R.string.llm_role_text),
            stringResource(R.string.llm_role_novel),
        ),
        selectedIndex = tab,
        onSelect = onSelect,
    )
}

@Composable
private fun PromptLanguageLine(langKey: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.llm_prompt_target_language),
            color = PikuColors.textSecondary,
            fontSize = 10.5.sp,
        )
        Text(
            text = stringResource(languageLabelRes(langKey)),
            color = PikuColors.textPrimary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.llm_prompt_follow_language),
            color = PikuColors.textFaint,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun PromptNote(text: String) {
    Text(
        text = text,
        color = PikuColors.textSecondary,
        fontSize = 10.5.sp,
        lineHeight = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(PikuColors.surfaceMuted, RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun PromptCard(
    role: String,
    text: String,
    defaultText: String,
    readOnly: Boolean,
    fromCatalog: Boolean,
    modified: Boolean,
    onTextChange: (String) -> Unit,
    onReset: () -> Unit,
    dark: Boolean,
) {
    val title = stringResource(
        if (role == Role.NOVEL) R.string.llm_prompt_novel_title else R.string.llm_prompt_text_title,
    )
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PikuColors.controlAccent,
        unfocusedBorderColor = PikuColors.border,
        cursorColor = PikuColors.controlAccent,
        focusedTextColor = PikuColors.textPrimary,
        unfocusedTextColor = PikuColors.textPrimary,
        focusedContainerColor = if (dark) Color(0x14FFFFFF) else Color(0x0A000000),
        unfocusedContainerColor = if (dark) Color(0x14FFFFFF) else Color(0x0A000000),
        disabledContainerColor = if (dark) Color(0x14FFFFFF) else Color(0x0A000000),
    )

    SettingsCard(dark = dark) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = PikuColors.textPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                MiniPill(
                    stringResource(
                        if (fromCatalog) {
                            R.string.llm_builtin_prompt_source_catalog
                        } else {
                            R.string.llm_builtin_prompt_source_fallback
                        },
                    ),
                )
                if (modified) {
                    Spacer(Modifier.width(4.dp))
                    MiniPill(stringResource(R.string.llm_prompt_modified))
                }
                Spacer(Modifier.weight(1f))
                if (!readOnly && text.trim() != defaultText.trim()) {
                    TextButton(
                        onClick = onReset,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 4.dp,
                            vertical = 0.dp,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.llm_prompt_reset_default),
                            color = PikuColors.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                readOnly = readOnly,
                textStyle = TextStyle(
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                    color = PikuColors.textPrimary,
                ),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (readOnly) 300.dp else 260.dp)
                    .padding(top = 8.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp, start = 2.dp),
            ) {
                Text(
                    text = stringResource(
                        if (readOnly) {
                            R.string.llm_builtin_prompt_readonly
                        } else {
                            R.string.llm_prompt_save_hint
                        },
                    ),
                    color = PikuColors.textFaint,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.llm_prompt_char_count, text.length),
                    color = PikuColors.textFaint,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

private fun promptPageBackground(dark: Boolean): Brush = Brush.verticalGradient(
    if (dark) listOf(HomeBgTopDark, HomeBgBottomDark)
    else listOf(HomeBgTopLight, HomeBgBottomLight),
)

private fun languageLabelRes(langKey: String): Int = when (langKey) {
    "en" -> R.string.language_en
    "ja" -> R.string.language_ja
    else -> R.string.language_zh
}
