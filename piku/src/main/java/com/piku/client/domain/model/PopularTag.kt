package com.piku.client.domain.model

data class PopularTag(
    val name: String,
    val genreId: Long? = null,
    val iconUrl: String? = null,
)