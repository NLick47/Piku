package com.piku.client.core.source

import com.piku.client.core.model.Work

data class SourcePage(
    val items: List<Work>,
    val totalPages: Int? = null,
    val nextCursor: String? = null,
)
