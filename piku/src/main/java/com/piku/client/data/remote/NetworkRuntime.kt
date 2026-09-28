package com.piku.client.data.remote

class NetworkRuntime(
    val now: () -> Long = System::currentTimeMillis,
    val sleeper: (Long) -> Unit = { millis ->
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    },
)
