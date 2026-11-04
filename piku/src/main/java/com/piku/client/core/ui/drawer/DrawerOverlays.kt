package com.piku.client.core.ui.drawer

import androidx.compose.runtime.Composable
import com.piku.client.core.ui.common.PikuFullScreenDialog

@Composable
fun FullscreenDrawerOverlay(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    PikuFullScreenDialog(
        onDismissRequest = onDismiss,
        content = content,
    )
}
