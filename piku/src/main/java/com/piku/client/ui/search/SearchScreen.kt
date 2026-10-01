package com.piku.client.ui.search


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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piku.client.R
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.model.Work
import com.piku.client.domain.source.SourceAuthorOpen
import com.piku.client.ui.common.FeedbackHost
import com.piku.client.ui.common.PikuBackButton
import com.piku.client.ui.theme.GlassHeaderTintDark
import com.piku.client.ui.theme.GlassHeaderTintLight
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.LoginTextSecondaryLight
import com.piku.client.ui.theme.PikuColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 与站点输入框一致的关键词长度上限（仅普通搜索；链接识别不受此限） */
private const val MAX_KEYWORD_LENGTH = 20

/** 待机态自动聚焦延迟，避免键盘与页面转场动画互相挤压 */
private const val FOCUS_DELAY_MS = 300L

/** 搜索页：关键词输入 → 作品 / 用户 / 标签 三个 tab 的统一结果页 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onWorkClick: (Work) -> Unit,
    onUserClick: (WorkSource, FollowUser) -> Unit,
    onOpenLink: (PoipikuLink) -> Unit,
    onOpenExternal: (String) -> Unit,
    onLoginClick: () -> Unit,
    onManageTags: () -> Unit,
    dark: Boolean = LocalDarkTheme.current,
) {
    val viewModel: SearchViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf(state.keyword) }
    val focusRequester = remember { FocusRequester() }
    var inputFocused by remember { mutableStateOf(false) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    // 去除 #/@ 前缀后的真实搜索词（# 可叠加，如站点分类标签 "##東方"）；空串表示待机态
    val searchTerm = state.keyword.trimStart('#').removePrefix("@").trim()
    val hasQuery = searchTerm.isNotEmpty()

    // 实时识别 poipiku 链接：命中后操作按钮切换为"打开链接"，提交时直接跳转不写历史
    val link = remember(query) { parsePoipikuLink(query) }

    // 一键译搜：中日互译——纯汉字→日语，含假名→中文；@ 用户搜索与链接不出现
    val scope = rememberCoroutineScope()
    var jaTranslateJob by remember { mutableStateOf<Job?>(null) }
    var jaTranslating by remember { mutableStateOf(false) }
    var jaFailed by remember { mutableStateOf(false) }
    val jaDirection = remember(query) { translateDirection(query) }

    // 联想层：聚焦 + 有输入 + 有联想结果时浮出；链接输入不联想
    val showSuggestions = inputFocused && link == null &&
        query.trim().isNotEmpty() && state.suggestions.isNotEmpty()

    // 用户行去向按插件声明分流（出站 / 应用内作者页），未声明走默认用户页
    val handleUserClick: (FollowUser) -> Unit = { user ->
        when (val open = viewModel.userOpen(user)) {
            is SourceAuthorOpen.External -> onOpenExternal(open.url)
            SourceAuthorOpen.NativeDetail, SourceAuthorOpen.NativeProfile, null ->
                onUserClick(viewModel.sourceId, user)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasQuery) {
            delay(FOCUS_DELAY_MS)
            focusRequester.requestFocus()
        }
    }

    FeedbackHost(channel = viewModel.feedback, snackbarHostState = snackbarHostState)

    fun doSubmit(raw: String) {
        val keyword = raw.trim()
        if (keyword.isEmpty()) return
        keyboardController?.hide()
        viewModel.clearSuggestions()
        val target = parsePoipikuLink(keyword)
        if (target != null) {
            // 链接直达：导航到作品/作者页，不写入搜索历史
            onOpenLink(target)
            return
        }
        viewModel.record(keyword)
        onSearch(keyword.take(MAX_KEYWORD_LENGTH))
    }

    fun submit(raw: String) {
        jaTranslateJob?.cancel()
        jaTranslating = false
        doSubmit(raw)
    }

    fun translateAndSearch() {
        if (jaTranslating) return
        val text = query
        val toJa = jaDirection != TranslateDirection.TO_ZH
        jaTranslateJob = scope.launch {
            jaTranslating = true
            jaFailed = false
            val translated = viewModel.translateKeyword(text, toJa)
            jaTranslating = false
            if (translated == null) {
                jaFailed = true
            } else if (translated == state.keyword) {
                // 译文就是当前已搜索词（同形词透传/缓存重复）：结果已在屏上，收键盘即可
                keyboardController?.hide()
            } else {
                doSubmit(translated)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (dark) listOf(HomeBgTopDark, HomeBgBottomDark)
                    else listOf(HomeBgTopLight, HomeBgBottomLight),
                ),
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            SearchTopBar(
                query = query,
                onQueryChange = { value ->
                    jaTranslateJob?.cancel()
                    jaTranslating = false
                    jaFailed = false
                    query = value
                    viewModel.onQueryInput(value)
                },
                onSubmit = { submit(query) },
                isLink = link != null,
                onBack = onBack,
                focusRequester = focusRequester,
                dark = dark,
                translateDirection = jaDirection,
                translateBusy = jaTranslating,
                translateFailed = jaFailed,
                onTranslateClick = { translateAndSearch() },
                onFocusedChanged = { inputFocused = it },
            )
            Box(Modifier.weight(1f)) {
                if (!hasQuery) {
                    IdleContent(
                        history = state.history,
                        popularTags = state.popularTagNames,
                        customTags = state.customTags,
                        trending = state.trending,
                        pluginHint = state.pluginActive,
                        onSelect = { submit(it) },
                        onSelectCustomTag = { submit("#$it") },
                        onManageTags = onManageTags,
                        onRemoveHistory = viewModel::removeHistory,
                        onClearHistory = viewModel::clearHistory,
                        dark = dark,
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        SearchTabRow(
                            selected = state.tab,
                            onSelect = viewModel::selectTab,
                        )
                        val hasFilterSpec = state.filterGroups.isNotEmpty() || state.filterToggles.isNotEmpty()
                        if (state.tab == SearchTab.WORKS && state.pluginActive && hasFilterSpec) {
                            SearchFilterBar(
                                groups = state.filterGroups,
                                toggles = state.filterToggles,
                                selected = state.selectedFilters,
                                onResetGroup = { groupId ->
                                    val defaultId = state.filterGroups
                                        .firstOrNull { it.id == groupId }
                                        ?.options?.firstOrNull { it.default }?.id
                                    if (defaultId != null) {
                                        viewModel.applyFilters(state.selectedFilters + (groupId to defaultId))
                                    }
                                },
                                onResetToggle = { toggleId ->
                                    viewModel.applyFilters(state.selectedFilters - toggleId)
                                },
                                onOpenSheet = { showFilterSheet = true },
                            )
                        }
                        when (state.tab) {
                            SearchTab.WORKS -> WorksTabContent(
                                state = state,
                                isTablet = isTablet,
                                onLoginClick = onLoginClick,
                                onRetry = viewModel::retryWorks,
                                onLoadMore = viewModel::loadMoreWorks,
                                onRetryLoadMore = viewModel::retryLoadMoreWorks,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onWorkClick = onWorkClick,
                                dark = dark,
                            )
                            SearchTab.USERS -> UsersTabContent(
                                state = state,
                                dark = dark,
                                onLoginClick = onLoginClick,
                                onRetry = viewModel::retryUsers,
                                onLoadMore = viewModel::loadMoreUsers,
                                onRetryLoadMore = viewModel::retryLoadMoreUsers,
                                onUserClick = handleUserClick,
                                onToggleFollow = viewModel::toggleFollow,
                            )
                            SearchTab.TAGS -> TagsTabContent(
                                state = state,
                                isTablet = isTablet,
                                onLoginClick = onLoginClick,
                                onRetry = viewModel::retryTags,
                                onLoadMore = viewModel::loadMoreTags,
                                onRetryLoadMore = viewModel::retryLoadMoreTags,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onTagClick = viewModel::selectTagCard,
                                onBackToSuggestions = viewModel::backToTagSuggestions,
                                onWorkClick = onWorkClick,
                                dark = dark,
                            )
                        }
                    }
                }
                if (showSuggestions) {
                    SuggestionScrim(onDismiss = { viewModel.clearSuggestions() })
                    SuggestionPanel(
                        query = query.trim(),
                        suggestions = state.suggestions,
                        onSelect = { submit(it) },
                        onDirectSearch = { submit(query) },
                        dark = dark,
                    )
                }
            }
        }
        if (showFilterSheet) {
            SearchFilterSheet(
                groups = state.filterGroups,
                toggles = state.filterToggles,
                selected = state.selectedFilters,
                onApply = { selected ->
                    viewModel.applyFilters(selected)
                    showFilterSheet = false
                },
                onDismiss = { showFilterSheet = false },
                dark = dark,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isLink: Boolean,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
    dark: Boolean,
    translateDirection: TranslateDirection?,
    translateBusy: Boolean,
    translateFailed: Boolean,
    onTranslateClick: () -> Unit,
    onFocusedChanged: (Boolean) -> Unit = {},
) {
    val primary = PikuColors.textPrimary
    val secondary = PikuColors.textSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (dark) GlassHeaderTintDark else GlassHeaderTintLight)
            .statusBarsPadding()
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PikuBackButton(
            onClick = onBack,
            dark = dark,
            contentDescription = stringResource(R.string.back),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(if (dark) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.9f))
                .border(
                    BorderStroke(
                        0.5.dp,
                        if (dark) Color.White.copy(alpha = 0.3f) else LoginTextSecondaryLight.copy(alpha = 0.15f),
                    ),
                    RoundedCornerShape(50),
                )
                .height(42.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = secondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_placeholder),
                        color = if (dark) LoginTextSecondaryDark else LoginTextFaintLight,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = primary),
                    cursorBrush = SolidColor(PikuColors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { onFocusedChanged(it.isFocused) },
                )
            }
            if (translateDirection != null) {
                val chipTint = if (translateFailed) PikuColors.error else PikuColors.accent
                val chipDesc = stringResource(
                    if (translateDirection == TranslateDirection.TO_JA) {
                        R.string.search_translate_ja
                    } else {
                        R.string.search_translate_zh
                    },
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(chipTint.copy(alpha = if (dark) 0.16f else 0.10f))
                        .clickable(enabled = !translateBusy, onClick = onTranslateClick)
                        .semantics { contentDescription = chipDesc },
                    contentAlignment = Alignment.Center,
                ) {
                    if (translateBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            strokeWidth = 1.4.dp,
                            color = chipTint,
                        )
                    } else {
                        Text(
                            text = "译",
                            color = chipTint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.search_clear),
                        tint = if (dark) LoginTextSecondaryDark else LoginTextFaintLight,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(if (isLink) R.string.search_open_link else R.string.search_action),
            color = PikuColors.accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onSubmit)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        )
    }
}

private enum class TranslateDirection { TO_JA, TO_ZH }

/** 方向判定：含假名按日语输入→译中文；纯汉字→译日语；英文/数字/空不给入口 */
private fun translateDirection(text: String): TranslateDirection? {
    val trimmed = text.trim()
    // @ 明确是用户搜索，用户名翻译没有意义，不出现译搜入口
    if (trimmed.startsWith("@")) return null
    var hasKana = false
    var hasHan = false
    for (ch in trimmed) {
        when (ch) {
            in '\u3040'..'\u30FF', in '\uFF66'..'\uFF9F' -> hasKana = true
            in '\u3400'..'\u4DBF', in '\u4E00'..'\u9FFF', in '\uF900'..'\uFAFF' -> hasHan = true
            else -> Unit
        }
    }
    return when {
        hasKana -> TranslateDirection.TO_ZH
        hasHan -> TranslateDirection.TO_JA
        else -> null
    }
}
