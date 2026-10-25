package com.piku.client.core.ui.novelcache

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piku.client.R
import com.piku.client.core.reader.FullNovelViewer

/** 从缓存直接打开的阅读器：不经过详情页，因此也不需要网络 */
@Composable
fun NovelCacheReaderScreen(onClose: () -> Unit) {
    val viewModel: NovelCacheReaderViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is NovelCacheReaderViewModel.ReaderState.Missing) onClose()
    }

    val novel = (state as? NovelCacheReaderViewModel.ReaderState.Ready)?.novel ?: return
    val chaptered = novel.chapters.size > 1
    FullNovelViewer(
        text = if (chaptered) "" else novel.chapters.firstOrNull()?.text.orEmpty(),
        title = novel.title,
        settings = settings,
        initialPercent = viewModel.initialPercent,
        onProgressSave = viewModel::saveProgress,
        onSettingsChange = viewModel::setSettings,
        onClose = onClose,
        onWorkClick = { _, _, _ -> },
        chapters = if (chaptered) novel.chapters else emptyList(),
        cacheStatus = if (chaptered) {
            stringResource(R.string.novel_cache_reader_cached, novel.chapters.size)
        } else {
            stringResource(R.string.novel_cache_reader_cached_single)
        },
    )
}
