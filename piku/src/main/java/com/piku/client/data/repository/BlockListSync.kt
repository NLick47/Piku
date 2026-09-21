package com.piku.client.data.repository

import android.util.Log
import com.piku.client.domain.model.AuthStatus
import com.piku.client.domain.usecase.LoadBlockUsersUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockListSync @Inject constructor(
    private val loadBlockUsersUseCase: LoadBlockUsersUseCase,
    private val blockListRepository: BlockListRepository,
    private val authRepository: AuthRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    /** 本次登录态是否已拉过：authStatus 重复发射时不重复请求 */
    @Volatile
    private var warmedForSession = false

    /** 登出/换账号时自增，用于丢弃在途分页结果，避免把上个账号的名单写回内存 */
    @Volatile
    private var generation = 0

    /** 由 Application 冷启动时调用一次 */
    fun start() {
        scope.launch {
            // 冷启动先让一头：首屏那一波请求（feed/资料/收藏）先发出去，预热不跟它们抢
            // HomeViewModel 会在 blockedIds 变化时重放快照重新过滤
            delay(COLD_START_DELAY_MS)
            authRepository.authStatus.collect { status ->
                if (status == AuthStatus.LOGGED_IN) {
                    warmUp()
                } else {
                    generation++
                    warmedForSession = false
                }
            }
        }
        scope.launch {
            // 自动重登成功：cookie 已换新，重新对齐一次
            authRepository.sessionRefreshed.collect { warmUp(force = true) }
        }
    }

    private fun warmUp(force: Boolean = false) {
        scope.launch {
            lock.withLock {
                if (!force && warmedForSession) return@withLock
                // 先置位再拉取：失败也不再重试，避免反复打服务端
                warmedForSession = true
                val gen = generation
                var page = 0
                while (page < MAX_PAGES) {
                    val users = loadBlockUsersUseCase(page).getOrElse { error ->
                        Log.d(TAG, "blocklist warm-up failed page=$page: $error")
                        return@withLock
                    }
                    // 拉取期间登出/换账号：结果作废（clear() 已清空名单）
                    if (gen != generation || !authRepository.isLoggedIn()) {
                        Log.d(TAG, "blocklist warm-up discarded page=$page")
                        return@withLock
                    }
                    blockListRepository.mergeFromServer(users)
                    if (users.isEmpty()) {
                        Log.d(TAG, "blocklist warm-up done pages=$page size=${blockListRepository.blockedIds.value.size}")
                        return@withLock
                    }
                    page++
                }
                Log.d(TAG, "blocklist warm-up hit cap=$MAX_PAGES size=${blockListRepository.blockedIds.value.size}")
            }
        }
    }

    private companion object {
        const val TAG = "PikuBlock"

        /** 屏蔽上百人的账号不把冷启动拖成串行请求，剩下的交给屏蔽列表页翻页补齐 */
        const val MAX_PAGES = 5

        /** 冷启动后的让位时间 */
        const val COLD_START_DELAY_MS = 800L
    }
}
