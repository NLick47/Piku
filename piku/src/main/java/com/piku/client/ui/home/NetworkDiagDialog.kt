package com.piku.client.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.piku.client.R
import com.piku.client.ui.theme.PikuColors
import kotlinx.coroutines.launch

@Composable
internal fun NetworkDiagDialog(
    text: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val copied = stringResource(R.string.network_diag_copied)
    val cleared = stringResource(R.string.network_diag_cleared)
    var field by remember { mutableStateOf(TextFieldValue(text)) }

    // 报告更新时保持用户已有的选区不跳走
    LaunchedEffect(text) {
        val selection = field.selection
        field = TextFieldValue(
            text = text,
            selection = TextRange(
                selection.start.coerceAtMost(text.length),
                selection.end.coerceAtMost(text.length),
            ),
        )
    }

    val background = PikuColors.surface
    val fieldBackground = PikuColors.surfaceMuted

    Dialog(onDismissRequest = onDismiss) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.86f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(background)
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.about_network_diag),
                        color = PikuColors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                            color = PikuColors.textSecondary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = stringResource(
                            if (loading) R.string.network_diag_running else R.string.network_diag_done,
                        ),
                        color = PikuColors.textFaint,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(fieldBackground)
                        .padding(10.dp),
                ) {
                    BasicTextField(
                        value = field,
                        onValueChange = { field = it },
                        readOnly = true,
                        textStyle = TextStyle(
                            color = PikuColors.textSecondary,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiagAction(stringResource(R.string.network_diag_copy), dark) {
                        clipboard.setText(AnnotatedString(field.text))
                        scope.launch { snackbarHostState.showSnackbar(copied) }
                    }
                    DiagAction(stringResource(R.string.network_diag_clear), dark) {
                        onClear()
                        scope.launch { snackbarHostState.showSnackbar(cleared) }
                    }
                    DiagAction(
                        text = stringResource(R.string.network_diag_refresh),
                        dark = dark,
                        enabled = !loading,
                        onClick = onRefresh,
                    )
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun DiagAction(
    text: String,
    dark: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(0.5.dp, PikuColors.border), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = if (enabled) PikuColors.textPrimary else PikuColors.textFaint,
            fontSize = 12.sp,
        )
    }
}
