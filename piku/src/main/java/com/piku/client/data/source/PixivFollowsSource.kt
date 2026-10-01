package com.piku.client.data.source

import com.piku.client.data.auth.PixivAuthRepository
import com.piku.client.data.remote.pixiv.PixivAppConfig
import com.piku.client.data.repository.PixivRepository
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.model.FollowUserPage
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.SourceAuthorOpen
import com.piku.client.domain.source.SourceFollows
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * pixiv 的「我的关注」能力：列表走 v1/user/following（user_id 传自己的数字 id），
 * 关注/取关走 v1/user/follow/add|delete，方向明确。悄悄关注（private）不在公开列表里。
 */
@Singleton
class PixivFollowsSource @Inject constructor(
    private val repository: PixivRepository,
    private val authRepository: PixivAuthRepository,
) : SourceFollows {

    override val sourceId = WorkSource.PIXIV

    override val loggedIn: Boolean get() = authRepository.isLoggedIn()

    override val sessionVersion: StateFlow<Long> = authRepository.sessionVersion

    override suspend fun follows(page: Int): Result<FollowUserPage> {
        val userId = authRepository.currentUserId()
            ?: return Result.failure(AppError.Unknown)
        return repository.userFollowing(userId, offset = page * PixivAppConfig.PAGE_SIZE)
            // 翻过末页接口回 404（2026-09 榜单同款行为）而非空列表：翻页中的 NotFound 就地判到底
            .recoverCatching { error ->
                if (page > 0 && error == AppError.NotFound) FollowUserPage(users = emptyList()) else throw error
            }
    }

    override suspend fun setFollowed(userId: Long, follow: Boolean): Result<Boolean> =
        repository.followUser(userId, follow).map { follow }

    // 应用内还没有 pixiv 用户页，与检索/详情页一致出站到 pixiv
    override fun userPage(user: FollowUser): SourceAuthorOpen =
        SourceAuthorOpen.External("https://www.pixiv.net/users/${user.userId}")
}
