package com.piku.client.source.pixiv.data.auth

import kotlinx.coroutines.CoroutineDispatcher

class PixivAuthRuntime(
    val dispatcher: CoroutineDispatcher,
    val now: () -> Long,
)
