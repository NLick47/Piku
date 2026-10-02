package com.piku.client.data.source

import com.piku.client.data.auth.PixivAuthRepository
import com.piku.client.data.local.QuietFollowStore
import com.piku.client.data.remote.pixiv.PixivAppConfig
import com.piku.client.data.repository.PixivRepository
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.model.FollowUserPage
import com.piku.client.domain.model.WorkSource
import com.piku.client.domain.source.SourceAuthorOpen
import com.piku.client.domain.source.SourceFollows
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * pixiv 的「我的关注」能力：列表走 v1/user/following（user_id 传自己的数字 id），
 * 关注/取关走 v1/user/follow/add|delete，方向明确。
 *
 * 关注列表页是公开/悄悄双 tab，各自独立分页；悄悄关注（restrict=private，
 * 非公開フォロー）不在公开列表里，由 [follows] 的 quiet 维度直取。
 */
@Singleton
class PixivFollowsSource @Inject constructor(
    private val repository: PixivRepository,
    private val authRepository: PixivAuthRepository,
    private val quietFollowStore: QuietFollowStore,
) : SourceFollows {

    override val sourceId = WorkSource.PIXIV

    override val loggedIn: Boolean get() = authRepository.isLoggedIn()

    override val sessionVersion: StateFlow<Long> = authRepository.sessionVersion

    override val quietSupported: Boolean = true

    override suspend fun follows(page: Int, quiet: Boolean): Result<FollowUserPage> {
        val userId = authRepository.currentUserId()
            ?: return Result.failure(AppError.Unknown)
        val restrict = if (quiet) PixivAppConfig.RESTRICT_PRIVATE else PixivAppConfig.RESTRICT_PUBLIC
        return try {
            val fetched = repository.userFollowing(userId, offset = page * PixivAppConfig.PAGE_SIZE, restrict = restrict)
                .getOrElse { error ->
                    // 翻过末页接口回 404（2026-09 榜单同款行为）而非空列表：就地判到底。
                    // 取消不是业务错误：原样上抛，别包成 failure 骗过壳子的错误态
                    if (error is CancellationException) throw error
                    if (error == AppError.NotFound) FollowUserPage(users = emptyList()) else throw error
                }
            // 名单校准：私密页里的必然悄悄、公开页里的必然公开——网页端那边的转档在这里归位
            fetched.users.forEach {
                if (quiet) quietFollowStore.mark(it.userId) else quietFollowStore.unmark(it.userId)
            }
            Result.success(fetched)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    override suspend fun setFollowed(userId: Long, follow: Boolean): Result<Boolean> =
        repository.followUser(userId, follow).map { follow }

    // 画师主页由主壳承载，与检索/详情页一致
    override fun userPage(user: FollowUser): SourceAuthorOpen = SourceAuthorOpen.NativeProfile
}
