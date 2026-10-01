package com.piku.client.data.source

import com.piku.client.data.repository.AuthRepository
import com.piku.client.data.repository.DetailRepository
import com.piku.client.data.repository.FeedRepository
import com.piku.client.data.repository.FollowResult
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
 * poipiku 的「我的关注」能力：列表走关注设置页解析，关注/取关走 UpdateFollowUserF。
 * 切换接口不收方向，按切换后的**实际**结果回报——调用方给的期望态只是意图。
 */
@Singleton
class PoipikuFollowsSource @Inject constructor(
    private val feedRepository: FeedRepository,
    private val detailRepository: DetailRepository,
    private val authRepository: AuthRepository,
) : SourceFollows {

    override val sourceId = WorkSource.POIPIKU

    override val loggedIn: Boolean get() = authRepository.isLoggedIn()

    override val sessionVersion: StateFlow<Long> = authRepository.sessionVersion

    override suspend fun follows(page: Int): Result<FollowUserPage> =
        feedRepository.getFollowUsers(page)

    override suspend fun setFollowed(userId: Long, follow: Boolean): Result<Boolean> {
        val result = detailRepository.updateFollow(userId)
        return when (result) {
            is FollowResult.Followed -> Result.success(true)
            is FollowResult.Unfollowed -> Result.success(false)
            // 登录态丢失或被拒：无法保证期望态，按失败回报
            is FollowResult.NotLoggedIn, is FollowResult.Failure -> Result.failure(AppError.Unknown)
        }
    }

    override fun userPage(user: FollowUser): SourceAuthorOpen = SourceAuthorOpen.NativeDetail
}
