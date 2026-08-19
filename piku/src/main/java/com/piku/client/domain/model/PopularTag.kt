package com.piku.client.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PopularTag(
    val name: String,
    val genreId: Long? = null,
    val iconUrl: String? = null,
)