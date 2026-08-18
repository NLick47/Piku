package com.piku.client.domain.model

data class FollowUser(
    val userId: Long,
    val name: String,
    val avatarUrl: String?,
    /** 是否已关注：关注列表页解析恒有值；作者搜索页按卡片标记解析，缺失视为 false */
    val followed: Boolean = false,
)

data class FollowUserPage(
    val users: List<FollowUser>,
    val total: Int,
)
