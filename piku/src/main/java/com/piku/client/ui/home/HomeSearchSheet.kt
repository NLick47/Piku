package com.piku.client.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.piku.client.ui.theme.LocalDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
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
import com.piku.client.R
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.LoginTextFaintDark
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.LoginTextSecondaryLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 与站点输入框一致的关键词长度上限 */
private const val MAX_KEYWORD_LENGTH = 20

/** 面板收起动画时长，结束后再真正移除组件，保证退出动画完整播放 */
private const val EXIT_ANIM_MS = 300L

/** 历史区最大高度，超出内部滚动，避免面板随键盘整体被顶高 */
private const val HISTORY_MAX_HEIGHT_DP = 250

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeSearchSheet(
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val dark = LocalDarkTheme.current
    val searchViewModel: SearchViewModel = hiltViewModel()
    val history by searchViewModel.history.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val transition = remember { MutableTransitionState(false).apply { targetState = true } }
    var closing by remember { mutableStateOf(false) }

    // 等滑入动画基本完成后再聚焦，避免键盘在动画中途弹出导致面板高度被 IME 压缩、内容跳变
    LaunchedEffect(Unit) {
        delay(250)
        focusRequester.requestFocus()
    }

    fun closeAnd(action: () -> Unit) {
        if (closing) return
        closing = true
        keyboardController?.hide()
        transition.targetState = false
        scope.launch {
            delay(EXIT_ANIM_MS)
            action()
        }
    }

    fun submit(raw: String) {
        val keyword = raw.trim()
        if (keyword.isEmpty()) return
        closeAnd {
            searchViewModel.record(keyword)
            onSearch(keyword)
        }
    }

    BackHandler {
        closeAnd { onDismiss() }
    }

    Box(Modifier.fillMaxSize()) {
        // 半透明遮罩（点击空白关闭）
        AnimatedVisibility(
            visibleState = transition,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (dark) Color(0x59000000) else Color(0x33000000))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { closeAnd { onDismiss() } },
            )
        }

        // 底部弹出面板：近不透明纯色底，圆角 + 细描边的玻璃质感
        AnimatedVisibility(
            visibleState = transition,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically(
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                initialOffsetY = { it },
            ) + fadeIn(tween(220)),
            exit = slideOutVertically(tween(260)) + fadeOut(tween(200)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        // 近不透明底色（方案 B：去掉实时模糊，保留纯色玻璃质感）
                        .background(if (dark) Color(0xF51C1A18) else Color(0xFBF5F3F0))
                        .border(
                            BorderStroke(
                                0.5.dp,
                                if (dark) Color(0x59FFFFFF) else Color(0x8CFFFFFF),
                            ),
                            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        )
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = 12.dp),
                ) {
                    SearchDragHandle(dark = dark)

                    SearchInputRow(
                        query = query,
                        onQueryChange = { query = it.take(MAX_KEYWORD_LENGTH) },
                        onSearch = { submit(query) },
                        onCancel = { closeAnd { onDismiss() } },
                        focusRequester = focusRequester,
                        dark = dark,
                    )

                    SearchRecentHeader(
                        hasHistory = history.isNotEmpty(),
                        onClear = { searchViewModel.clearHistory() },
                        dark = dark,
                    )

                    // 历史区固定上限高度 + 内部滚动：键盘弹出时面板不会被整体顶高
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = HISTORY_MAX_HEIGHT_DP.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                        ) {
                            if (history.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    history.forEach { keyword ->
                                        SearchKeywordChip(
                                            keyword = keyword,
                                            onClick = { submit(keyword) },
                                            onDelete = { searchViewModel.remove(keyword) },
                                            dark = dark,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.search_hint),
                        color = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchDragHandle(dark: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (dark) Color(0x40FFFFFF) else Color(0x408A857C)),
        )
    }
}

@Composable
private fun SearchInputRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
    focusRequester: FocusRequester,
    dark: Boolean,
) {
    val primary = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
    val secondary = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight
    val faint = if (dark) LoginTextFaintDark else LoginTextFaintLight

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.search_placeholder),
                    color = faint,
                    fontSize = 14.sp,
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
                                tint = faint,
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
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .focusRequester(focusRequester)
                .weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.search_cancel),
            color = primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SearchRecentHeader(
    hasHistory: Boolean,
    onClear: () -> Unit,
    dark: Boolean,
) {
    val secondary = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight
    val faint = if (dark) LoginTextFaintDark else LoginTextFaintLight

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            tint = secondary,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.search_recent),
            color = secondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (hasHistory) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = null,
                    tint = faint,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = stringResource(R.string.search_clear),
                    color = faint,
                    fontSize = 11.sp,
                )
            }
        }
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
                tint = if (dark) LoginTextFaintDark else LoginTextFaintLight,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}
