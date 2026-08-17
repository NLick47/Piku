package com.piku.client.domain.model

data class FollowUser(
    val userId: Long,
    val name: String,
    val avatarUrl: String?,
)

data class FollowUserPage(
    val users: List<FollowUser>,
    val total: Int,
)
