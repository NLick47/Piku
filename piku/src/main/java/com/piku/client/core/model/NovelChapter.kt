package com.piku.client.core.model

data class NovelChapter(
    val title: String,
    val text: String,
) {
    val length: Int get() = text.length
}
