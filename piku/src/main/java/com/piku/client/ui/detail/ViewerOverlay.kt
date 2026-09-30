package com.piku.client.ui.detail

import androidx.compose.runtime.Composable

@Composable
fun ViewerOverlay(
    /** 当前打开的图页；null = 关闭态，不渲染 */
    page: Int?,
    images: List<ViewerImage>,
    dark: Boolean,
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
) {
    if (page == null || images.isEmpty()) return
    FullScreenViewer(
        images = images,
        startPage = page.coerceIn(0, images.lastIndex),
        dark = dark,
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
    )
}
