package com.piku.client.core.model

data class BookmarkPage(
    val items: List<Work>,
    val nextCursor: String?,
)
