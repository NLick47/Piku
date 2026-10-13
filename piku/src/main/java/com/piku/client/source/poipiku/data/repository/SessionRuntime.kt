package com.piku.client.source.poipiku.data.repository

import kotlinx.coroutines.CoroutineDispatcher

class SessionRuntime(
    val dispatcher: CoroutineDispatcher,
    val now: () -> Long,
)
