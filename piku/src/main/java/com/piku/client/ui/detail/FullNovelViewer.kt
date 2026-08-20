package com.piku.client.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.common.LinkSegment
import com.piku.client.common.LinkText
import com.piku.client.data.local.SettingsRepository
import com.piku.client.ui.theme.ControlAccentDark
import com.piku.client.ui.theme.ControlAccentLight
import com.piku.client.ui.theme.ViewerBackgroundDark
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal const val NOVEL_FONT_MIN = SettingsRepository.NOVEL_FONT_MIN
internal const val NOVEL_FONT_MAX = SettingsRepository.NOVEL_FONT_MAX
internal const val NOVEL_FONT_DEFAULT = SettingsRepository.NOVEL_FONT_DEFAULT

private const val AUTO_HIDE_DELAY_MS = 2500L
private val POIPIKU_WORK_REGEX = Regex("""https?://poipiku\.com/(\d+)/(\d+)\.html""")

/** 阅读器浅色（米色纸）配色：不跟随系统主题，独立切换 */
internal val NovelReaderBgLight = Color(0xFFF3EEDA)
internal val NovelReaderTextLight = Color(0xFF2E2A23)
/** 阅读器深色配色 */
internal val NovelReaderBgDark = ViewerBackgroundDark
internal val NovelReaderTextDark = Color(0xFFD6D0C4)

/**
 * 全屏小说阅读器：
 * - 独立配色（浅米底深字 / 深底浅字），与系统主题无关，由用户显式切换并持久化
 * - 字号 A−/A+ 调节（[NOVEL_FONT_MIN]~[NOVEL_FONT_MAX]），持久化
 * - 长按文本可选中复制（SelectionContainer）
 * - 点击文本区域切换顶部/底部控制栏显隐（自动隐藏）
 */
@Composable
fun FullNovelViewer(
    text: String,
    title: String,
    fontSize: Float,
    light: Boolean,
    initialPercent: Int,
    onProgressSave: (Int) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLightChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onWorkClick: (Long, Long, String) -> Unit,
) {
    val context = LocalContext.current
    val currentOnWorkClick by rememberUpdatedState(onWorkClick)
    val currentOnProgressSave by rememberUpdatedState(onProgressSave)
    var controlsVisible by remember { mutableStateOf(true) }
    var autoHideJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val scrollState = remember { ScrollState(0) }

    fun refreshAutoHide() {
        controlsVisible = true
        autoHideJob?.cancel()
        autoHideJob = scope.launch {
            delay(AUTO_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(Unit) { refreshAutoHide() }

    // 恢复进度：内容可滚动后按保存的百分比跳转。百分比与字号/内容长度无关，
    // 字号调整后也不会错位；maxValue 为 0（不足一屏）则保持顶部。
    LaunchedEffect(scrollState, initialPercent) {
        if (initialPercent <= 0) return@LaunchedEffect
        val max = snapshotFlow { scrollState.maxValue }
            .filter { it > 0 }
            .first()
        scrollState.scrollTo((initialPercent / 100f * max).toInt().coerceIn(0, max))
    }

    // 阅读进度：滚动停止约 1s 保存一次百分比（杀进程也不丢），退出时兜底再保存
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }
            .debounce(800)
            .collect {
                if (scrollState.maxValue > 0) currentOnProgressSave(progressPercent(scrollState))
            }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (scrollState.maxValue > 0) currentOnProgressSave(progressPercent(scrollState))
        }
    }

    val bg = if (light) NovelReaderBgLight else NovelReaderBgDark
    val fg = if (light) NovelReaderTextLight else NovelReaderTextDark
    val linkColor = if (light) ControlAccentLight else ControlAccentDark
    val controlBg = if (light) Color(0xE6F3EEDA) else Color(0xCC141312)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    if (controlsVisible) {
                        controlsVisible = false
                        autoHideJob?.cancel()
                    } else {
                        refreshAutoHide()
                    }
                })
            },
    ) {
        BackHandler(onBack = onClose)

        SelectionContainer(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, top = 64.dp, bottom = 88.dp),
        ) {
            Text(
                text = remember(text, linkColor, context, currentOnWorkClick) {
                    linkifyNovel(text, linkColor, context, currentOnWorkClick)
                },
                color = fg,
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.7f).sp,
            )
        }

        // 顶部栏：返回 + 标题
        if (controlsVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(controlBg)
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.detail_fullscreen_close),
                        tint = fg,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = title,
                    color = fg,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 底部设置栏：字号 A− / 配色切换 / 字号 A+
        if (controlsVisible) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(controlBg)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReaderFontButton(
                    label = "A−",
                    enabled = fontSize > NOVEL_FONT_MIN,
                    onClick = { onFontSizeChange(fontSize - 1f) },
                    fg = fg,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(
                            R.string.detail_novel_font_size,
                            fontSize.toInt(),
                        ),
                        color = fg,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = stringResource(
                            R.string.detail_novel_progress,
                            progressPercent(scrollState),
                        ),
                        color = fg.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                    )
                }
                IconButton(onClick = { onLightChange(!light) }) {
                    Icon(
                        imageVector = if (light) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                        contentDescription = stringResource(
                            if (light) R.string.detail_novel_theme_dark
                            else R.string.detail_novel_theme_light,
                        ),
                        tint = fg,
                        modifier = Modifier.size(20.dp),
                    )
                }
                ReaderFontButton(
                    label = "A+",
                    enabled = fontSize < NOVEL_FONT_MAX,
                    onClick = { onFontSizeChange(fontSize + 1f) },
                    fg = fg,
                )
            }
        }
    }
}

/** 阅读进度百分比（0~100；正文不足一屏无需滚动时视为已读完，显示 100） */
private fun progressPercent(scrollState: ScrollState): Int {
    val max = scrollState.maxValue
    if (max <= 0) return 100
    return ((scrollState.value.toFloat() / max) * 100).toInt().coerceIn(0, 100)
}

@Composable
private fun ReaderFontButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    fg: Color,
) {
    val alpha = if (enabled) 1f else 0.35f
    Text(
        text = label,
        color = fg.copy(alpha = alpha),
        fontSize = 16.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** 小说正文链接化：poipiku 作品内链点击跳转详情，外链用系统浏览器打开 */
private fun linkifyNovel(
    raw: String,
    linkColor: Color,
    context: Context,
    onWorkClick: (Long, Long, String) -> Unit,
): AnnotatedString {
    val style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    return buildAnnotatedString {
        for (segment in LinkText.parse(raw)) {
            when (segment) {
                is LinkSegment.Plain -> append(segment.text)
                is LinkSegment.Link -> {
                    val workMatch = POIPIKU_WORK_REGEX.matchEntire(segment.url)
                    if (workMatch != null) {
                        val authorId = workMatch.groupValues[1].toLongOrNull()
                        val workId = workMatch.groupValues[2].toLongOrNull()
                        withLink(
                            LinkAnnotation.Clickable(tag = segment.url) {
                                if (authorId != null && workId != null) {
                                    onWorkClick(authorId, workId, "")
                                }
                            },
                        ) {
                            pushStyle(style)
                            append(segment.text)
                            pop()
                        }
                    } else {
                        withLink(
                            LinkAnnotation.Clickable(tag = segment.url) {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(segment.url)),
                                )
                            },
                        ) {
                            pushStyle(style)
                            append(segment.text)
                            pop()
                        }
                    }
                }
            }
        }
    }
}