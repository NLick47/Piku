package com.piku.client.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PopularTag(
    val name: String,
    val genreId: Long? = null,
    val iconUrl: String? = null,
    val thumbnailUrl: String? = null,
)