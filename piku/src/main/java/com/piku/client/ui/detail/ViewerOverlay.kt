package com.piku.client.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.piku.client.data.local.MangaReaderSettings

@Composable
fun ViewerOverlay(
    /** 当前打开的图页；null = 关闭态，不渲染 */
    page: Int?,
    images: List<ViewerImage>,
    title: String,
    settings: MangaReaderSettings,
    onSettingsChange: (MangaReaderSettings, Boolean) -> Unit,
    onClose: () -> Unit,
    onLongPressImage: (Int) -> Unit,
    hasImageModel: Boolean,
    imageTranslating: Boolean,
    imageTranslated: Boolean,
    translatedImages: Map<Int, android.graphics.Bitmap>,
    onImageTranslateClick: (Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    hdPages: Set<Int> = emptySet(),
    onHdToggle: (Int) -> Unit = {},
    /** 详情页图区已上屏的图：各页的零延迟垫底，没有的页回落加载 */
    previews: Map<Int, Painter> = emptyMap(),
) {
    if (page == null || images.isEmpty()) return
    MangaReader(
        images = images,
        startPage = page.coerceIn(0, images.lastIndex),
        title = title,
        settings = settings,
        onSettingsChange = onSettingsChange,
        onClose = onClose,
        onLongPressImage = onLongPressImage,
        hasImageModel = hasImageModel,
        imageTranslating = imageTranslating,
        imageTranslated = imageTranslated,
        translatedImages = translatedImages,
        onImageTranslateClick = onImageTranslateClick,
        onPageChanged = onPageChanged,
        hdPages = hdPages,
        onHdToggle = onHdToggle,
        previews = previews,
    )
}
