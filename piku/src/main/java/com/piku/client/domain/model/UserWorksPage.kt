package com.piku.client.domain.model

/** 用户作品列表的一页结果：作品列表 + 用户主页头部信息（仅第一页解析，分页为 null） */
data class UserWorksPage(
    val works: List<Work>,
    val pageInfo: UserPageInfo? = null,
)
