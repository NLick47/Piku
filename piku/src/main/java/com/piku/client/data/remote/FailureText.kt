package com.piku.client.data.remote

import kotlinx.coroutines.CancellationException

internal fun Throwable.isCancellation(): Boolean =
    generateSequence(this) { it.cause }.any { it is InterruptedException || it is CancellationException }

internal fun Throwable.describeChain(levels: Int = 3, maxChars: Int = 160): String {
    val chain = generateSequence(this) { it.cause }
        .take(levels)
        .joinToString(" ← ") { error ->
            val message = error.message?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
            error.javaClass.simpleName + message
        }
    val suppressed = if (suppressed.isNotEmpty()) "（另有 ${suppressed.size} 条失败）" else ""
    return (chain + suppressed).take(maxChars)
}
