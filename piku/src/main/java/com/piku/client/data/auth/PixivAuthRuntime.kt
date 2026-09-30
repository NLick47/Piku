package com.piku.client.data.auth

import kotlinx.coroutines.CoroutineDispatcher

class PixivAuthRuntime(
    val dispatcher: CoroutineDispatcher,
    val now: () -> Long,
)
