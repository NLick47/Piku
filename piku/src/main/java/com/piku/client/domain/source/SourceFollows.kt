package com.piku.client.domain.source

import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.model.FollowUserPage
import com.piku.client.domain.model.WorkSource
import kotlinx.coroutines.flow.StateFlow

/**
 * 关注插件：源声明「我的关注」的数据能力——已关注用户列表与关注/取关。
 * 列表页壳子（poipiku 关注页那套 UI）只认这个协议，不认识任何站点；
 * 没有实现的源不出现「我的关注」入口。
 */
interface SourceFollows {

    val sourceId: WorkSource

    /** 本源当前是否已登录；未登录时列表页给登录门而不是发请求 */
    val loggedIn: Boolean

    /**
     * 会话版本：登录/登出/换号时自增，列表页据此重拉（订阅时持有的当前值不触发）。
     * 令牌静默续期不算会话变化——列表数据仍然有效，不该让用户看着列表闪一下。
     */
    val sessionVersion: StateFlow<Long>

    /** 本源是否支持悄悄关注（pixiv 非公開フォロー）：决定关注列表页出不出公开/悄悄双 tab */
    val quietSupported: Boolean get() = false

    /**
     * 一页已关注用户；page 从 0 起。[quiet] = 悄悄关注段（restrict=private），
     * 不支持悄悄关注的源忽略它。total = null 表示源不给出总数，翻页以空页为准
     */
    suspend fun follows(page: Int, quiet: Boolean = false): Result<FollowUserPage>

    /** 关注/取关一个用户；成功返回**动作完成后**的实际关注态 */
    suspend fun setFollowed(userId: Long, follow: Boolean): Result<Boolean>

    /** 点关注的用户行去哪：应用内主页或出站网页，与 [SourceSearch.userPage] 同款约定 */
    fun userPage(user: FollowUser): SourceAuthorOpen
}
