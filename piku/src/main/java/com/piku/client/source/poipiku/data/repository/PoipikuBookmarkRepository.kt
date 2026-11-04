package com.piku.client.source.poipiku.data.repository

import com.piku.client.core.model.AppError
import com.piku.client.core.network.http.apiCall
import com.piku.client.source.poipiku.data.remote.PoipikuApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * poipiku 账号书签（远程收藏）的写入。
 *
 * 接口是 **toggle**：只翻转当前状态，不接收"我要加/我要删"。因此调用方不能
 * 拿本地收藏状态直接当方向——必须先看返回的 result，与期望不一致就再翻一次。
 * 这样既不用预拉全量列表判断远端状态，也不会把网页上已有的书签误删。
 *
 * 自动镜像与收藏页的手动同步可能同时发生，所有写操作共用一把 Mutex，
 * 避免两次 toggle 交错把最终状态翻反。
 */
@Singleton
class PoipikuBookmarkRepository @Inject constructor(
    private val api: PoipikuApi,
    private val authRepository: AuthRepository,
) {

    private val toggleMutex = Mutex()

    /** 当前登录账号标识；手动批量同步用它中止换号后的剩余任务 */
    fun currentAccountKey(): String? = authRepository.currentUserId()?.toString()

    /**
     * 把作品书签收敛到 [desired] 状态。
     *
     * 最多两次 toggle：第一次结果已经是期望值就直接成功；否则说明远端本来就是期望值，
     * 只是被这次 toggle 翻走了，再翻一次即可。第二次仍不对（并发客户端插了一手）按失败返回。
     */
    suspend fun setBookmarked(workId: Long, desired: Boolean): Result<Unit> =
        toggleMutex.withLock {
            apiCall {
                val uid = authRepository.currentUserId() ?: throw AppError.LoginRequired
                var state = toggle(uid, workId)
                if (state != desired) {
                    state = toggle(uid, workId)
                }
                if (state != desired) {
                    throw BookmarkStateMismatch(desired, state)
                }
            }
        }

    private suspend fun toggle(uid: Long, workId: Long): Boolean {
        val response = api.updateBookmark(uid, workId)
        return when (response.result) {
            1 -> true
            0 -> false
            -2 -> throw AppError.LoginRequired
            else -> throw BookmarkRejected(response.result, response.msg)
        }
    }

    /** 服务端返回了非 0/1 的 result */
    class BookmarkRejected(val code: Int, val serverMsg: String) :
        Exception("bookmark rejected: $code $serverMsg")

    /** 两次 toggle 后仍未达到期望状态（通常是并发修改） */
    class BookmarkStateMismatch(desired: Boolean, actual: Boolean) :
        Exception("bookmark state mismatch: desired=$desired actual=$actual")
}
