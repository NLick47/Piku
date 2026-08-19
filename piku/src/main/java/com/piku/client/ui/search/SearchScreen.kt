package com.piku.client.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.piku.client.R
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.model.TagCard
import com.piku.client.domain.model.Work
import com.piku.client.ui.common.FollowPillButton
import com.piku.client.ui.common.GlassCard
import com.piku.client.ui.common.LoaderDots
import com.piku.client.ui.common.LoginPrompt
import com.piku.client.ui.common.UserAvatar
import com.piku.client.ui.common.WorkCard
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.LoginCardBorderDark
import com.piku.client.ui.theme.LoginCardBorderLight
import com.piku.client.ui.theme.LoginCardDark
import com.piku.client.ui.theme.LoginCardLight
import com.piku.client.ui.theme.LoginTextFaintDark
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.LoginTextSecondaryLight
import com.piku.client.ui.theme.PillBorderDark
import com.piku.client.ui.theme.PillBorderLight
import com.piku.client.ui.theme.TameWhiteColorFilter
import com.piku.client.ui.theme.WorkCardBgDark
import com.piku.client.ui.theme.WorkCardBorderDark
import com.piku.client.ui.theme.WorkCardInfoBgDark
import com.piku.client.ui.theme.WorkCardPlaceholderDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

/** 与站点输入框一致的关键词长度上限 */
private const val MAX_KEYWORD_LENGTH = 20

/** 待机态自动聚焦延迟，避免键盘与页面转场动画互相挤压 */
private const val FOCUS_DELAY_MS = 300L

/** 搜索页：关键词输入 → 作品 / 用户 / 标签 三个 tab 的统一结果页 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onWorkClick: (Work) -> Unit,
    onUserClick: (FollowUser) -> Unit,
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

    // 去除 #/@ 前缀后的真实搜索词；空串表示待机态（历史 + 热门标签）
    val searchTerm = state.keyword.removePrefix("#").removePrefix("@").trim()
    val hasQuery = searchTerm.isNotEmpty()

    LaunchedEffect(Unit) {
        if (!hasQuery) {
            delay(FOCUS_DELAY_MS)
            focusRequester.requestFocus()
        }
    }

    val feedbackMessage = state.actionFeedbackRes?.let { stringResource(it) }
    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            snackbarHostState.showSnackbar(feedbackMessage)
            viewModel.clearFeedback()
        }
    }

    fun submit(raw: String) {
        val keyword = raw.trim()
        if (keyword.isEmpty()) return
        keyboardController?.hide()
        viewModel.record(keyword)
        onSearch(keyword)
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
                onQueryChange = { query = it.take(MAX_KEYWORD_LENGTH) },
                onSubmit = { submit(query) },
                onBack = onBack,
                focusRequester = focusRequester,
                dark = dark,
            )
            if (!hasQuery) {
                IdleContent(
                    history = state.history,
                    popularTags = state.popularTagNames,
                    customTags = state.customTags,
                    onSelect = { submit(it) },
                    onSelectCustomTag = { submit("#$it") },
                    onManageTags = onManageTags,
                    onRemoveHistory = viewModel::removeHistory,
                    onClearHistory = viewModel::clearHistory,
                    dark = dark,
                )
            } else {
                SearchTabRow(
                    selected = state.tab,
                    onSelect = viewModel::selectTab,
                    dark = dark,
                )
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
                        onUserClick = onUserClick,
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
    onBack: () -> Unit,
    focusRequester: FocusRequester,
    dark: Boolean,
) {
    val primary = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
    val secondary = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (dark) Color(0xF2262421) else Color(0xF7FFFFFF))
            .statusBarsPadding()
            .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = primary,
                modifier = Modifier.size(20.dp),
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.search_placeholder),
                    color = if (dark) LoginTextSecondaryDark else LoginTextFaintLight,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = secondary,
                    modifier = Modifier.size(17.dp),
                )
            },
            trailingIcon = {
                // 固定占位，避免清空按钮出现/消失时输入框跳动
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.search_clear),
                                tint = if (dark) LoginTextSecondaryDark else LoginTextFaintLight,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            textStyle = TextStyle(fontSize = 15.sp, color = primary),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (dark) Color(0x40FFFFFF) else Color(0xD9FFFFFF),
                unfocusedContainerColor = if (dark) Color(0x40FFFFFF) else Color(0xD9FFFFFF),
                focusedBorderColor = if (dark) Color(0x66FFFFFF) else Color(0x59A09A92),
                unfocusedBorderColor = if (dark) Color(0x40FFFFFF) else Color(0x40A09A92),
                cursorColor = if (dark) LoginTextPrimaryDark else AccentDark,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.search_action),
            color = if (dark) LoginTextPrimaryDark else AccentDark,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onSubmit)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdleContent(
    history: List<String>,
    popularTags: List<String>,
    customTags: List<String>,
    onSelect: (String) -> Unit,
    onSelectCustomTag: (String) -> Unit,
    onManageTags: () -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    dark: Boolean,
) {
    val label = if (dark) LoginTextSecondaryDark else LoginTextFaintLight
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
    ) {
        MyTagsRow(
            tags = customTags,
            activeTag = null,
            onSelect = onSelectCustomTag,
            onManage = onManageTags,
            dark = dark,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = label,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.search_recent),
                color = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (history.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClearHistory)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                        tint = label,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.search_clear),
                        color = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (history.isEmpty()) {
            Text(
                text = stringResource(R.string.search_history_empty),
                color = label,
                fontSize = 12.sp,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                history.forEach { keyword ->
                    SearchKeywordChip(
                        keyword = keyword,
                        onClick = { onSelect(keyword) },
                        onDelete = { onRemoveHistory(keyword) },
                        dark = dark,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.search_hot_tags),
            color = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(10.dp))
        if (popularTags.isEmpty()) {
            Text(
                text = stringResource(R.string.search_hot_tags_empty),
                color = label,
                fontSize = 12.sp,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                popularTags.forEach { tag ->
                    TagPill(
                        text = "#$tag",
                        active = false,
                        onClick = { onSelect("#$tag") },
                        dark = dark,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.search_hint),
            color = label,
            fontSize = 11.sp,
        )
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyTagsRow(
    tags: List<String>,
    activeTag: String?,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = if (dark) LoginTextSecondaryDark else LoginTextFaintLight
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Label,
                contentDescription = null,
                tint = label,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.menu_my_tags),
                color = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onManage)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.search_manage_tags),
                    color = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (tags.isEmpty()) {
            Text(
                text = stringResource(R.string.my_tags_empty),
                color = label,
                fontSize = 12.sp,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    TagPill(
                        text = "#$tag",
                        active = tag == activeTag,
                        onClick = { onSelect(tag) },
                        dark = dark,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchTabRow(
    selected: SearchTab,
    onSelect: (SearchTab) -> Unit,
    dark: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SearchTabItem(
            text = stringResource(R.string.search_tab_works),
            active = selected == SearchTab.WORKS,
            onClick = { onSelect(SearchTab.WORKS) },
            dark = dark,
        )
        SearchTabItem(
            text = stringResource(R.string.search_tab_users),
            active = selected == SearchTab.USERS,
            onClick = { onSelect(SearchTab.USERS) },
            dark = dark,
        )
        SearchTabItem(
            text = stringResource(R.string.search_tab_tags),
            active = selected == SearchTab.TAGS,
            onClick = { onSelect(SearchTab.TAGS) },
            dark = dark,
        )
    }
}

@Composable
private fun SearchTabItem(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            color = when {
                active -> if (dark) LoginTextPrimaryDark else AccentDark
                else -> if (dark) LoginTextSecondaryDark else Color(0xFF8A8A8A)
            },
            fontSize = 15.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .width(if (active) 18.dp else 0.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (dark) LoginTextPrimaryDark else AccentDark),
        )
    }
}

@Composable
private fun TagPill(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                when {
                    active -> if (dark) LoginTextPrimaryDark else Color(0xFF2C2C2C)
                    else -> if (dark) Color(0x40FFFFFF) else Color(0xE6FFFFFF)
                },
            )
            .border(
                BorderStroke(
                    0.5.dp,
                    when {
                        active -> if (dark) LoginTextPrimaryDark else Color(0xFF2C2C2C)
                        else -> if (dark) PillBorderDark else PillBorderLight
                    },
                ),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = when {
                active -> if (dark) LoginBackgroundDark else Color.White
                else -> if (dark) LoginTextSecondaryDark else Color(0xFF5A5A5A)
            },
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
    }
}

@Composable
private fun SearchKeywordChip(
    keyword: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (dark) Color(0x40FFFFFF) else Color(0xE6FFFFFF))
            .border(
                BorderStroke(0.5.dp, if (dark) Color(0x47FFFFFF) else Color(0x66A09A92)),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = keyword,
            color = if (dark) LoginTextSecondaryDark else Color(0xFF5A5A5A),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp),
        )
        Box(
            modifier = Modifier
                .padding(start = 2.dp)
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.search_delete),
                tint = if (dark) LoginTextSecondaryDark else LoginTextFaintLight,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

@Composable
private fun WorksTabContent(
    state: SearchUiState,
    isTablet: Boolean,
    onLoginClick: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onToggleFavorite: (Work) -> Unit,
    onWorkClick: (Work) -> Unit,
    dark: Boolean,
) {
    Column(Modifier.fillMaxSize()) {
        when {
            state.worksNeedLogin -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoginPrompt(
                        message = stringResource(R.string.search_works_login),
                        onLogin = onLoginClick,
                        dark = dark,
                    )
                }
            }
            state.worksLoading && state.works.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoaderDots(dark = dark)
                }
            }
            state.worksErrorRes != null && state.works.isEmpty() -> {
                SearchErrorState(errorRes = state.worksErrorRes, onRetry = onRetry, dark = dark)
            }
            state.works.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.search_empty),
                        color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                        fontSize = 14.sp,
                    )
                }
            }
            else -> {
                SearchWorkGrid(
                    works = state.works,
                    favoriteIds = state.favoriteIds,
                    isTablet = isTablet,
                    loadingMore = state.worksLoadingMore,
                    loadMoreErrorRes = state.worksLoadMoreErrorRes,
                    endReached = state.worksEndReached,
                    onLoadMore = onLoadMore,
                    onRetryLoadMore = onRetryLoadMore,
                    onToggleFavorite = onToggleFavorite,
                    onWorkClick = onWorkClick,
                    dark = dark,
                )
            }
        }
    }
}

@Composable
private fun TagsTabContent(
    state: SearchUiState,
    isTablet: Boolean,
    onLoginClick: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onToggleFavorite: (Work) -> Unit,
    onTagClick: (String) -> Unit,
    onBackToSuggestions: () -> Unit,
    onWorkClick: (Work) -> Unit,
    dark: Boolean,
) {
    val selectedTag = state.selectedTagName
    Column(Modifier.fillMaxSize()) {
        if (selectedTag != null) {
            TagWorksHeader(tag = selectedTag, onBack = onBackToSuggestions, dark = dark)
        }
        when {
            state.tagNeedLogin -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoginPrompt(
                        message = stringResource(R.string.search_tags_login),
                        onLogin = onLoginClick,
                        dark = dark,
                    )
                }
            }
            selectedTag == null -> {
                // 建议模式：标签卡片（原站行为：始终先展示标签，点击后才出作品）
                when {
                    state.tagSuggestionsLoading && state.tagSuggestions.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoaderDots(dark = dark)
                        }
                    }
                    state.tagSuggestionsErrorRes != null && state.tagSuggestions.isEmpty() -> {
                        SearchErrorState(errorRes = state.tagSuggestionsErrorRes, onRetry = onRetry, dark = dark)
                    }
                    state.tagSuggestions.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.search_tags_not_found),
                                color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    else -> {
                        TagCardGrid(
                            cards = state.tagSuggestions,
                            isTablet = isTablet,
                            loadingMore = state.tagSuggestionsLoadingMore,
                            loadMoreErrorRes = state.tagSuggestionsLoadMoreErrorRes,
                            endReached = state.tagSuggestionsEndReached,
                            onLoadMore = onLoadMore,
                            onRetryLoadMore = onRetryLoadMore,
                            onTagClick = onTagClick,
                            dark = dark,
                        )
                    }
                }
            }
            else -> {
                // 作品模式：选中精确标签下的作品
                when {
                    state.tagWorksLoading && state.tagWorks.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoaderDots(dark = dark)
                        }
                    }
                    state.tagWorksErrorRes != null && state.tagWorks.isEmpty() -> {
                        SearchErrorState(errorRes = state.tagWorksErrorRes, onRetry = onRetry, dark = dark)
                    }
                    state.tagWorks.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.search_tags_empty),
                                color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    else -> {
                        SearchWorkGrid(
                            works = state.tagWorks,
                            favoriteIds = state.favoriteIds,
                            isTablet = isTablet,
                            loadingMore = state.tagWorksLoadingMore,
                            loadMoreErrorRes = state.tagWorksLoadMoreErrorRes,
                            endReached = state.tagWorksEndReached,
                            onLoadMore = onLoadMore,
                            onRetryLoadMore = onRetryLoadMore,
                            onToggleFavorite = onToggleFavorite,
                            onWorkClick = onWorkClick,
                            dark = dark,
                        )
                    }
                }
            }
        }
    }
}

/** 作品模式顶栏：返回标签建议 + 当前精确标签名 */
@Composable
private fun TagWorksHeader(
    tag: String,
    onBack: () -> Unit,
    dark: Boolean,
) {
    val primary = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = "#$tag",
            color = primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TagCardGrid(
    cards: List<TagCard>,
    isTablet: Boolean,
    loadingMore: Boolean,
    loadMoreErrorRes: Int?,
    endReached: Boolean,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onTagClick: (String) -> Unit,
    dark: Boolean,
) {
    val gridState = rememberLazyStaggeredGridState()
    val currentEndReached by rememberUpdatedState(endReached)
    val currentLoadingMore by rememberUpdatedState(loadingMore)
    val currentLoadMoreErrorRes by rememberUpdatedState(loadMoreErrorRes)

    LaunchedEffect(gridState, cards.size) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 6
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd && !currentEndReached && !currentLoadingMore && currentLoadMoreErrorRes == null) {
                    onLoadMore()
                }
            }
    }

    LazyVerticalStaggeredGrid(
        columns = if (isTablet) StaggeredGridCells.Adaptive(220.dp) else StaggeredGridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        items(cards, key = { it.name }) { card ->
            TagCardItem(
                card = card,
                onClick = { onTagClick(card.name) },
                dark = dark,
            )
        }
        when {
            loadMoreErrorRes != null -> {
                item(span = StaggeredGridItemSpan.FullLine) {
                    SearchLoadMoreError(errorRes = loadMoreErrorRes, onRetry = onRetryLoadMore, dark = dark)
                }
            }
            loadingMore -> {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoaderDots(dark = dark)
                    }
                }
            }
            endReached -> {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.home_no_more),
                            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagCardItem(
    card: TagCard,
    onClick: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .shadow(
                elevation = if (dark) 6.dp else 10.dp,
                shape = shape,
                ambientColor = Color(0x33000000),
                spotColor = Color(0x40000000),
            )
            .clip(shape)
            .background(if (dark) WorkCardBgDark else Color(0xCCFFFFFF))
            .border(
                BorderStroke(1.dp, if (dark) WorkCardBorderDark else Color(0x59C8C2B8)),
                shape,
            )
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            if (card.thumbnailUrl.isNullOrBlank()) {
                // 无示例图（默认占位图）的标签：显示中性占位而非空白
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(if (dark) WorkCardPlaceholderDark else Color(0xFFF1EFEA)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Label,
                        contentDescription = "#${card.name}",
                        tint = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                        modifier = Modifier.size(32.dp),
                    )
                }
            } else {
                AsyncImage(
                    model = card.thumbnailUrl,
                    contentDescription = "#${card.name}",
                    colorFilter = if (dark) TameWhiteColorFilter else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(if (dark) WorkCardPlaceholderDark else Color(0xFFF1EFEA)),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(if (dark) WorkCardInfoBgDark else Color(0xF2FFFFFF))
                .padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            Text(
                text = "#${card.name}",
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchWorkGrid(
    works: List<Work>,
    favoriteIds: Set<String>,
    isTablet: Boolean,
    loadingMore: Boolean,
    loadMoreErrorRes: Int?,
    endReached: Boolean,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onToggleFavorite: (Work) -> Unit,
    onWorkClick: (Work) -> Unit,
    dark: Boolean,
) {
    val gridState = rememberLazyStaggeredGridState()
    val currentEndReached by rememberUpdatedState(endReached)
    val currentLoadingMore by rememberUpdatedState(loadingMore)
    val currentLoadMoreErrorRes by rememberUpdatedState(loadMoreErrorRes)

    LaunchedEffect(gridState, works.size) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 6
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd && !currentEndReached && !currentLoadingMore && currentLoadMoreErrorRes == null) {
                    onLoadMore()
                }
            }
    }

    LazyVerticalStaggeredGrid(
        columns = if (isTablet) StaggeredGridCells.Adaptive(220.dp) else StaggeredGridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        items(works, key = { it.id }) { work ->
            WorkCard(
                work = work,
                isFavorite = work.id.toString() in favoriteIds,
                onToggleFavorite = { onToggleFavorite(work) },
                onClick = { onWorkClick(work) },
                dark = dark,
            )
        }
        when {
            loadMoreErrorRes != null -> {
                item(span = StaggeredGridItemSpan.FullLine) {
                    SearchLoadMoreError(errorRes = loadMoreErrorRes, onRetry = onRetryLoadMore, dark = dark)
                }
            }
            loadingMore -> {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoaderDots(dark = dark)
                    }
                }
            }
            endReached -> {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.home_no_more),
                            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsersTabContent(
    state: SearchUiState,
    dark: Boolean,
    onLoginClick: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onUserClick: (FollowUser) -> Unit,
    onToggleFollow: (Long) -> Unit,
) {
    when {
        state.usersNeedLogin -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoginPrompt(
                    message = stringResource(R.string.search_users_login),
                    onLogin = onLoginClick,
                    dark = dark,
                )
            }
        }
        state.usersLoading && state.users.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoaderDots(dark = dark)
            }
        }
        state.usersErrorRes != null && state.users.isEmpty() -> {
            SearchErrorState(errorRes = state.usersErrorRes, onRetry = onRetry, dark = dark)
        }
        state.users.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.search_users_empty),
                    color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
            }
        }
        else -> {
            SearchUserList(
                state = state,
                dark = dark,
                onUserClick = onUserClick,
                onToggleFollow = onToggleFollow,
                onLoadMore = onLoadMore,
                onRetryLoadMore = onRetryLoadMore,
            )
        }
    }
}

@Composable
private fun SearchUserList(
    state: SearchUiState,
    dark: Boolean,
    onUserClick: (FollowUser) -> Unit,
    onToggleFollow: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val currentUsersEndReached by rememberUpdatedState(state.usersEndReached)
    val currentUsersLoadingMore by rememberUpdatedState(state.usersLoadingMore)
    val currentUsersLoadMoreErrorRes by rememberUpdatedState(state.usersLoadMoreErrorRes)

    LaunchedEffect(listState, state.users.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 6
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd && !currentUsersEndReached && !currentUsersLoadingMore && currentUsersLoadMoreErrorRes == null) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.users, key = { it.userId }) { user ->
            val followed = state.followOverrides[user.userId] ?: user.followed
            SearchUserRow(
                user = user,
                followed = followed,
                followSending = user.userId in state.followPendingIds,
                dark = dark,
                onClick = { onUserClick(user) },
                onToggleFollow = { onToggleFollow(user.userId) },
            )
        }
        when {
            state.usersLoadMoreErrorRes != null -> {
                item {
                    SearchLoadMoreError(errorRes = state.usersLoadMoreErrorRes, onRetry = onRetryLoadMore, dark = dark)
                }
            }
            state.usersLoadingMore -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        LoaderDots(dark = dark)
                    }
                }
            }
            state.usersEndReached && state.users.size >= 30 -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.home_no_more),
                            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchUserRow(
    user: FollowUser,
    followed: Boolean,
    followSending: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    onToggleFollow: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    GlassCard(
        dark = dark,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(avatarUrl = user.avatarUrl, onClick = onClick, dark = dark, size = 48.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "ID: ${user.userId}",
                    color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            FollowPillButton(
                unfollowed = !followed,
                sending = followSending,
                dark = dark,
                onClick = onToggleFollow,
            )
        }
    }
}

@Composable
private fun SearchErrorState(
    errorRes: Int?,
    onRetry: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(errorRes ?: R.string.home_error_parse),
            color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .clip(shape)
                .background(if (dark) LoginCardDark else LoginCardLight)
                .border(
                    BorderStroke(0.5.dp, if (dark) LoginCardBorderDark else LoginCardBorderLight),
                    shape,
                )
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.home_retry),
                color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SearchLoadMoreError(
    errorRes: Int?,
    onRetry: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .clip(shape)
            .background(if (dark) LoginCardDark else LoginCardLight)
            .border(
                BorderStroke(0.5.dp, if (dark) LoginCardBorderDark else LoginCardBorderLight),
                shape,
            )
            .clickable(onClick = onRetry)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(errorRes ?: R.string.home_error_parse),
            color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
            fontSize = 12.sp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.home_retry),
            color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
