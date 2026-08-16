package com.piku.client.domain.model

data class UserProfile(
    val uid: String?,
    val avatarUrl: String?,
    val profileUrl: String?,
    val name: String? = null,
)
