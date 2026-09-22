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
    /**
     * 访问门（App 应渲染的门卡类型）；null = 无需门卡。
     * 唯一决策点是 `DetailRepository`（结合作品属性、登录态、R-18 设置与服务端
     * append 响应），ViewModel/UI 只读这一个字段——历史上判定逻辑分散在多处，
     * 出现过"登录用户被展示登录门"等漂移回归。
     */
    val gate: RestrictionReason? = null,
    /**
     * 服务端对 append 的拒绝原文（未知错误码兜底）。与 poipiku 网页端行为一致：
     * 网页端遇到不认识的结果码就是把这句原文弹出来。null = 无。
     */
    val serverNotice: String? = null,
    /**
     * 登录限定作品（"仅登录用户可看"的作品属性，IllustItem class 含 Login）。
     * 注意：属性与当前登录态无关——登录后类名依旧带 Login、占位图也不变，
     * 服务端只在 append 响应里区分（匿名 -3 / 登录放行）。是否拦成登录门卡
     * 由 Repository 结合 App 登录态决定；登录后重新加载即可看到真实内容。
     */
    val loginRequired: Boolean = false,
    /**
     * poipiku 内「こっそりフォロー」限定（IllustItem class 含 Follower）。
     * 实测（2026-09）：未关注时 append 返回 -4 +「こっそりフォロー限定です」，
     * App 内关注作者（[followed]）后立即放行——解锁动作 App 可自助完成，
     * 门卡给「关注作者」而不是引导网页。此类作品常另设密码（关注者口令）。
     */
    val followerGate: Boolean = false,
    /**
     * Twitter 关注者限定（IllustItem class 含 TFollower）。服务端经 Twitter
     * 验证关注关系（append -5 + TwitterFollowerLimitInfoDlg），解锁在 Twitter
     * 侧完成，App 内无自助动作，门卡只能引导网页端。
     */
    val twitterFollowerGate: Boolean = false,
    /** 当前登录用户是否已关注该作者（详情页 HTML 中 UserInfoCmdFollow 的 Selected 类） */
    val followed: Boolean = false,
    /**
     * 当前登录用户是否已屏蔽该作者（页头 UserInfoCmdBlock 的 Selected 类）。
     * 被屏蔽的作者作品页会 302 跳其主页，因此正常能解析出详情时该项恒为 false，
     * 仅在用户主页等场景下可能为 true。
     */
    val blocked: Boolean = false,
    /**
     * AI 译文（原文字段保持不变）。null 表示未翻译；
     * 展示由 UI 的原文/译文开关控制，复制与历史记录始终用原文。
     */
    val translated: TranslatedFields? = null,
)
