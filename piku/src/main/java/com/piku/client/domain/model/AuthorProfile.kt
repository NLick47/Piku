package com.piku.client.domain.model


data class AuthorProfile(
    val userId: Long,
    val name: String,
    /** pixiv ID，没有则空串；展示为 @account */
    val account: String = "",
    val avatarUrl: String? = null,
    /** 横幅图；不在受管图片上游时取不到，界面退回主题渐变 */
    val bannerUrl: String? = null,
    val comment: String = "",
    /** null = 该源不提供这一项 */
    val illustCount: Int? = null,
    val mangaCount: Int? = null,
    val novelCount: Int? = null,
    /** 公开收藏数（私密收藏数接口不给） */
    val bookmarkCount: Int? = null,
    val followCount: Int? = null,
    val twitterUrl: String? = null,
    val webpage: String? = null,
    val premium: Boolean = false,
    val followed: Boolean = false,
)
