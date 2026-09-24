package com.piku.client.data.repository

import kotlinx.coroutines.CoroutineDispatcher

class SessionRuntime(
    val dispatcher: CoroutineDispatcher,
    val now: () -> Long,
)
