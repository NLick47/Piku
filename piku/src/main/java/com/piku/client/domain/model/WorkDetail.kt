package com.piku.client.domain.model

data class WorkDetail(
    val title: String,
    val description: String = "",
    val authorName: String,
    val authorAvatarUrl: String,
    val authorProfile: String = "",
    val categoryCd: Int,
    val categoryName: String,
    val imageUrls: List<String>,
    val tags: List<String>,
    val reactions: List<String> = emptyList(),
    val reactionCounts: Map<String, Int> = emptyMap(),
    val reactionCount: Int = 0,
    val relatedWorks: List<Work> = emptyList(),
    val r18: Boolean,
    val warning: Boolean = false,
    val passwordProtected: Boolean = false,
    val passwordError: Boolean = false,
    /**
     * 密码正确但解锁被服务器拒绝（append result_num=-4：需关联 Twitter 账号等）。
     * 与 [passwordError] 不同：密码本身没错，无需重新输入，但当前账号无法查看。
     */
    val unlockBlocked: Boolean = false,
    /** 服务器返回的阻塞原因提示（如"请关联 Twitter 账号"），未提供时为空 */
    val unlockBlockedMessage: String = "",
    val novelText: String = "",
    val adultLocked: Boolean = false,
    /** 当前登录用户是否已关注该作者（详情页 HTML 中 UserInfoCmdFollow 的 Selected 类） */
    val followed: Boolean = false,
)
