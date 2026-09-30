package com.piku.client.data.auth

internal fun pixivAuthHeaders(host: String, accessToken: String?): List<Pair<String, String>> =
    if (host == PixivAuthEndpoints.PIXIV_APP_API_HOST && !accessToken.isNullOrBlank()) {
        listOf("Authorization" to "Bearer $accessToken")
    } else {
        emptyList()
    }
