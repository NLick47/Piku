package com.piku.client.domain.source

import com.piku.client.domain.model.Work

data class SourcePage(
    val items: List<Work>,
    val totalPages: Int? = null,
)
