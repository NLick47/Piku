package com.piku.client.core.model

data class UserProfile(
    val uid: String?,
    val avatarUrl: String?,
    val profileUrl: String?,
    val name: String? = null,
)
