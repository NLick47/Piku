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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
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
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 18.dp),
        ) {
            PromptTabs(tab = tab, onSelect = { tab = it })
            Spacer(Modifier.height(14.dp))
            PromptLanguageLine(langKey = state.promptLangKey)
            PromptNote(stringResource(R.string.llm_builtin_prompt_note))
            Spacer(Modifier.height(18.dp))
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
                color = PikuColors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(start = 2.dp, top = 14.dp),
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
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 18.dp),
        ) {
            PromptTabs(tab = tab, onSelect = { tab = it })
            Spacer(Modifier.height(14.dp))
            PromptLanguageLine(langKey = langKey)
            PromptNote(stringResource(R.string.llm_provider_prompt_note))
            Spacer(Modifier.height(18.dp))
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
                color = PikuColors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(start = 2.dp, top = 14.dp),
            )
        }
        SettingsFooter(dark = dark) {
            Text(
                text = stringResource(R.string.llm_prompt_apply_hint),
                color = PikuColors.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 7.dp),
            )
            LlmActionButton(
                text = stringResource(R.string.llm_prompt_done),
                primary = true,
                onClick = { applyAndBack() },
                modifier = Modifier.fillMaxWidth(),
            )
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
        modifier = Modifier.padding(start = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.llm_prompt_target_language),
            color = PikuColors.textSecondary,
            fontSize = 11.sp,
        )
        Text(
            text = stringResource(languageLabelRes(langKey)),
            color = PikuColors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.llm_prompt_follow_language),
            color = PikuColors.textFaint,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun PromptNote(text: String) {
    Text(
        text = text,
        color = PikuColors.textSecondary,
        fontSize = 11.sp,
        lineHeight = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 10.dp),
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
    SettingsCard(dark = dark) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = PikuColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
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
                    LlmTextAction(
                        text = stringResource(R.string.llm_prompt_reset_default),
                        onClick = onReset,
                        color = PikuColors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LlmTextArea(
                value = text,
                onValueChange = onTextChange,
                readOnly = readOnly,
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
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.llm_prompt_char_count, text.length),
                    color = PikuColors.textFaint,
                    fontSize = 11.sp,
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
