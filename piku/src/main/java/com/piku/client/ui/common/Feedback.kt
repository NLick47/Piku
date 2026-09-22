package com.piku.client.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class FeedbackText(
    @StringRes val res: Int = 0,
    val args: List<Any> = emptyList(),
    /** 服务端原文等无法用资源 ID 表达的文本；非 null 时优先于 [res] */
    val raw: String? = null,
)

data class FeedbackMessage(
    val text: FeedbackText,
    @StringRes val actionLabelRes: Int? = null,
    val onAction: (() -> Unit)? = null,
)


class FeedbackChannel {

    private val _messages = MutableSharedFlow<FeedbackMessage>(extraBufferCapacity = BUFFER_CAPACITY)
    val messages: SharedFlow<FeedbackMessage> = _messages.asSharedFlow()

    fun show(@StringRes res: Int, vararg args: Any) {
        _messages.tryEmit(FeedbackMessage(FeedbackText(res, args.toList())))
    }

    /** 原样展示一段文本（如 poipiku 服务端返回的拒绝原文，与网页端 DispMsg 对齐） */
    fun showText(text: String) {
        _messages.tryEmit(FeedbackMessage(FeedbackText(raw = text)))
    }

    fun showAction(@StringRes res: Int, @StringRes actionLabelRes: Int, onAction: () -> Unit) {
        _messages.tryEmit(
            FeedbackMessage(
                text = FeedbackText(res),
                actionLabelRes = actionLabelRes,
                onAction = onAction,
            ),
        )
    }

    private companion object {
        const val BUFFER_CAPACITY = 8
    }
}

fun FeedbackText.resolve(context: Context): String = when {
    raw != null -> raw
    args.isEmpty() -> context.getString(res)
    else -> context.getString(res, *args.toTypedArray())
}

@Composable
fun FeedbackHost(
    channel: FeedbackChannel,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    LaunchedEffect(channel, snackbarHostState) {
        channel.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text.resolve(context),
                actionLabel = message.actionLabelRes?.let { context.getString(it) },
            )
            if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
        }
    }
}
