package com.piku.client.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

fun CoroutineScope.reloadOnSessionChange(
    version: StateFlow<Long>,
    block: suspend () -> Unit,
): Job {
    val seenAtSubscribe = version.value
    return launch {
        var seen = seenAtSubscribe
        version.collect { current ->
            if (current == seen) return@collect
            seen = current
            block()
        }
    }
}
