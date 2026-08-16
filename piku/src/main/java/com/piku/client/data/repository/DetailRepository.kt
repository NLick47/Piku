package com.piku.client.data.repository

import android.util.Log
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.local.WorkPasswordRepository
import com.piku.client.data.remote.PoipikuApi
import com.piku.client.data.remote.SessionMonitor
import com.piku.client.data.remote.WorkDetailParser
import com.piku.client.data.remote.apiCall
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ReactionResult {
    data object Success : ReactionResult
    data object LimitReached : ReactionResult
    data class Failure(val error: Throwable) : ReactionResult
}

sealed interface FollowResult {
    data object Followed : FollowResult
    data object Unfollowed : FollowResult
    data object NotLoggedIn : FollowResult
    data class Failure(val message: String = "") : FollowResult
}

@Singleton
class DetailRepository @Inject constructor(
    private val api: PoipikuApi,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val thumbnailResolver: ThumbnailResolver,
    private val sessionMonitor: SessionMonitor,
    private val workPasswordRepository: WorkPasswordRepository,
) {

    /**
     * 拉取作品详情。密码提交解锁时传入 [existing]（已解析的锁页 detail）可跳过重复的
     * 详情页 HTML 请求（Web 解锁也只发一次 append POST）；带密码的请求不受全局
     * append 限速等待，保证解锁立即发出。
     */
    suspend fun getWorkDetail(
        work: Work,
        password: String = "",
        existing: WorkDetail? = null,
    ): Result<WorkDetail> = apiCall {
        val loggedIn = authRepository.isLoggedIn()
        val detail = existing ?: run {
            val html = api.getWorkDetail(work.authorId, work.id).string()
            WorkDetailParser.parse(html)
        }
        if (detail.warning && !settingsRepository.showAdultContent.first()) {
            return@apiCall detail.copy(imageUrls = emptyList(), adultLocked = true)
        }
        if (detail.passwordProtected && password.isBlank()) {
            return@apiCall detail.copy(imageUrls = emptyList())
        }
        if (detail.warning) {
            val result = coroutineScope {
                thumbnailResolver.throttleAppend(force = password.isNotBlank())
                val appendD = async { api.showAppendFile(work.authorId, work.id, password, 0, -1) }
                val fullD = if (loggedIn) {
                    async { runCatching { getWorkFullImages(work, password).getOrThrow() }.getOrDefault(emptyList()) }
                } else {
                    null
                }
                val appendResp = appendD.await()
                notifySessionInvalidIfNeeded(appendResp.result_num)
                val appendUrls = if (appendResp.result_num > 0) {
                    WorkDetailParser.extractImageUrls(appendResp.html)
                } else {
                    emptyList()
                }
                // 回填列表缩略图缓存（append 返回的 _640 图，原图带签名不适合缓存）
                appendUrls.firstOrNull()?.let { thumbnailResolver.rememberThumb(enrichedWork(work, detail), it) }
                val novelText = WorkDetailParser.extractNovelText(appendResp.html)
                val passwordError = appendResp.result_num == -2
                val unlockBlocked = appendResp.result_num == -4
                val fullUrls = fullD?.await().orEmpty()
                val urls = if (loggedIn) fullUrls.ifEmpty { appendUrls } else appendUrls
                Log.d(TAG, "detail warning urls=${urls.size} novel=${novelText.length} pwError=$passwordError blocked=$unlockBlocked work=${work.authorId}/${work.id} loggedIn=$loggedIn")
                rememberWorkPassword(work, password, appendResp.result_num)
                if (unlockBlocked) {
                    detail.copy(
                        imageUrls = emptyList(),
                        passwordError = false,
                        unlockBlocked = true,
                        unlockBlockedMessage = WorkDetailParser.extractUnlockBlockedMessage(appendResp.html),
                        novelText = novelText,
                    )
                } else {
                    detail.copy(imageUrls = urls, passwordError = passwordError, novelText = novelText)
                }
            }
            return@apiCall result
        }
        val adultEnabled = settingsRepository.showAdultContent.first()
        val appendResp = if (detail.r18 && !adultEnabled) {
            null
        } else {
            thumbnailResolver.throttleAppend(force = password.isNotBlank())
            runCatching {
                api.showAppendFile(work.authorId, work.id, password, 0, -1)
            }.getOrNull()
        }
        appendResp?.let { notifySessionInvalidIfNeeded(it.result_num) }
        val appendUrls = if (appendResp?.result_num != null && appendResp.result_num > 0) {
            WorkDetailParser.extractImageUrls(appendResp.html)
        } else {
            emptyList()
        }
        // 回填列表缩略图缓存：点开详情拿到真实图后，列表立即显示
        appendUrls.firstOrNull()?.let { thumbnailResolver.rememberThumb(enrichedWork(work, detail), it) }
        val novelText = appendResp?.html?.let { WorkDetailParser.extractNovelText(it) }.orEmpty()
        val passwordError = appendResp?.result_num == -2
        val unlockBlocked = appendResp?.result_num == -4
        Log.d(
            TAG,
            "detail normal append work=${work.authorId}/${work.id} result_num=${appendResp?.result_num} " +
                "r18=${detail.r18} html=${detail.imageUrls} appendUrls=$appendUrls",
        )
        // 详情页 HTML 提供第 1 张（主图），append 返回第 2 张起的追加图；
        // 合并时过滤 sign in/R-18 等占位图，只保留真实图
        val urls = when {
            // 密码作品解锁失败（密码错误 -2 / 账号受限 -4）：保持锁页，
            // 避免显示 HTML 主图造成"已解锁"假象（追加图实际未解锁）
            detail.passwordProtected && (passwordError || unlockBlocked) -> emptyList()
            passwordError -> detail.imageUrls
            else -> ThumbnailResolver.mergeWorkImages(detail.imageUrls, appendUrls)
        }
        rememberWorkPassword(work, password, appendResp?.result_num ?: 0)
        if (unlockBlocked) {
            detail.copy(
                imageUrls = emptyList(),
                passwordError = false,
                unlockBlocked = true,
                unlockBlockedMessage = appendResp?.html?.let {
                    WorkDetailParser.extractUnlockBlockedMessage(it)
                }.orEmpty(),
                novelText = novelText,
            )
        } else {
            detail.copy(imageUrls = urls, passwordError = passwordError, novelText = novelText)
        }
    }

    /**
     * 用解析出的 detail 补全详情页传入的瘦 work（title/authorName 等可能为空），
     * 供缩略图回填事件携带完整字段，避免消费方拿到空标题。
     */
    private fun enrichedWork(work: Work, detail: WorkDetail): Work = work.copy(
        title = detail.title,
        authorName = detail.authorName,
        authorAvatarUrl = detail.authorAvatarUrl.ifBlank { null },
        categoryName = detail.categoryName,
        imageCount = detail.imageUrls.size,
        r18 = detail.r18,
        warning = detail.warning,
    )

    /**
     * 自动保存作品密码（供下次自动进入）：密码经服务端验证有效时保存/覆盖。
     * 有效判定：result_num > 0（解锁成功）或 -4（密码正确但账号受限，如需 Twitter
     * 关联——实测错误密码返回 -2，-4 只在密码正确时出现）。这是唯一写入途径，
     * 无任何 UI 可查看或修改。数据库异常不影响解锁结果。
     */
    private suspend fun rememberWorkPassword(work: Work, password: String, resultNum: Int) {
        val save = password.isNotBlank() && (resultNum > 0 || resultNum == -4)
        Log.d(TAG, "rememberWorkPassword work=${work.id} resultNum=$resultNum pw=${password.isNotBlank()} save=$save")
        if (!save) return
        runCatching { workPasswordRepository.savePassword(work.id, password) }
    }

    suspend fun getWorkFullImages(work: Work, password: String = ""): Result<List<String>> = apiCall {
        val response = api.showIllustDetail(work.authorId, work.id, -1, password)
        notifySessionInvalidIfNeeded(response.error_code)
        val fullUrls = if (response.error_code != 0) {
            emptyList()
        } else {
            WorkDetailParser.extractFullImageUrls(response.html)
        }
        if (fullUrls.isNotEmpty()) return@apiCall fullUrls
        thumbnailResolver.throttleAppend(force = password.isNotBlank())
        val ads = runCatching {
            WorkDetailParser.extractAppendAds(
                api.showAppendFile(work.authorId, work.id, password, 0, -1).html
            )
        }.getOrDefault(emptyList())
        ads.mapNotNull { ad ->
            runCatching {
                val r = api.showIllustDetail(work.authorId, work.id, ad, password)
                if (r.error_code != 0) null
                else WorkDetailParser.extractFullImageUrls(r.html).firstOrNull()
            }.getOrNull()
        }
    }

    /**
     * 登录态下收到"需要登录"错误码（-3）时通知 session 失效，触发自动重登。
     * -1 为エラー/限流、-2 为密码错误，均不触发，避免限流导致的重登风暴。
     * 未登录时的同类错误属正常现象，不触发。
     */
    private fun notifySessionInvalidIfNeeded(code: Int) {
        if (code == ThumbnailResolver.RESULT_LOGIN_REQUIRED && authRepository.isLoggedIn()) {
            sessionMonitor.notifySessionCleared()
        }
    }

    suspend fun sendReaction(workId: Long, emoji: String, userId: Long): ReactionResult = try {
        val resp = api.sendEmoji(workId, emoji, userId)
        Log.d(TAG, "sendReaction work=$workId emoji=$emoji uid=$userId result_num=${resp.result_num} error_code=${resp.error_code} result=${resp.result.take(80)}")
        when {
            resp.result_num > 0 -> ReactionResult.Success
            resp.error_code == -40 -> ReactionResult.LimitReached
            else -> ReactionResult.Failure(AppError.Unknown)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ReactionResult.Failure(e)
    }

    /**
     * 关注/取消关注作者（POST /f/UpdateFollowUserF.jsp，UID=当前用户、IID=目标作者）。
     * 未登录直接返回 [FollowResult.NotLoggedIn]，不发请求。
     */
    suspend fun updateFollow(targetUserId: Long): FollowResult = try {
        val uid = authRepository.currentUserId()
        if (uid == null) return FollowResult.NotLoggedIn
        val resp = api.updateFollowUser(uid, targetUserId)
        Log.d(TAG, "updateFollow target=$targetUserId uid=$uid result=${resp.result} btn_label=${resp.btn_label} err=${resp.err_msg}")
        when (resp.result) {
            1 -> FollowResult.Followed
            2 -> FollowResult.Unfollowed
            else -> FollowResult.Failure(resp.err_msg)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        FollowResult.Failure(e.message.orEmpty())
    }

    private companion object {
        const val TAG = "PikuDiag"
    }
}