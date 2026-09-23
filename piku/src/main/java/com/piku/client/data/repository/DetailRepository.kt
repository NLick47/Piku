package com.piku.client.data.repository

import android.util.Log
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.local.WorkPasswordRepository
import com.piku.client.data.remote.PoipikuApi
import com.piku.client.data.remote.SessionMonitor
import com.piku.client.data.remote.WorkDetailParser
import com.piku.client.data.remote.apiCall
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.RestrictionReason
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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

sealed interface BlockResult {
    data object Blocked : BlockResult
    data object Unblocked : BlockResult
    data object NotLoggedIn : BlockResult
    data class Failure(val message: String = "") : BlockResult
}

@Singleton
class DetailRepository @Inject constructor(
    private val api: PoipikuApi,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val thumbnailResolver: ThumbnailResolver,
    private val sessionMonitor: SessionMonitor,
    private val workPasswordRepository: WorkPasswordRepository,
    private val blockListRepository: BlockListRepository,
) {

    /**
     * 拉取作品详情。密码提交解锁时传入 [existing]（已解析的锁页 detail）可跳过重复的
     * 详情页 HTML 请求（Web 解锁也只发一次 append POST）；带密码的请求不受全局
     * append 限速等待，保证解锁立即发出。
     *
     * [onPartial] 是"先出首屏"用的阶段一回调：详情页 HTML 解析完、门判定按 HTML 定下
     * 之后立刻回调一次，UI 可先把标题/描述/标签/作者/主图画出来，不必等限速等待与
     * append（网页端同样是 HTML 到达即渲染、追加图异步补）。触发条件见
     * [DetailLoadPolicy.partialFromHtml]——只有 HTML 已给出真实主图的作品才提前画。
     */
    suspend fun getWorkDetail(
        work: Work,
        password: String = "",
        existing: WorkDetail? = null,
        onPartial: (suspend (WorkDetail) -> Unit)? = null,
    ): Result<WorkDetail> = apiCall {
        val startedAt = System.nanoTime()
        val loggedIn = authRepository.isLoggedIn()
        // 锁页 detail 的 imageUrls 已被下方 passwordProtected 分支清空（为显示密码框）
        // 直接复用会导致解锁合并（mergeWorkImages）丢失详情页主图，需重新解析 HTML 取回
        val detail = if (existing != null && existing.imageUrls.isNotEmpty()) {
            existing
        } else {
            // HTML 解析在主线程上跑过：详情页 HTML 有几百 KB、十几轮正则（含相关作品
            // 逐条解析），放在 ViewModel 的主线程协程里会卡住出内容那一帧
            withContext(Dispatchers.Default) {
                val html = api.getWorkDetail(work.authorId, work.id).string()
                WorkDetailParser.parse(html)
            }
        }
        val htmlMs = elapsedMs(startedAt)
        // ---- 门判定（唯一决策点：结果写入 detail.gate，UI 只渲染该字段）----
        // 依据 = 作品属性（IllustItem class / 密码框；服务端静态渲染，反映作品设置
        // 而非浏览者状态，必须结合登录态）+ 服务端 append 响应（真正的权限判定，
        // 逆向自 common.js：-2 密码错 / -3 需登录 / -4 拒绝附原因 / -5 键推限定 /
        // -20 需转推）。历史上判定分散在多处导致过多次回归，这里统一收口。
        val adultEnabled = settingsRepository.showAdultContent.first()
        fun gated(reason: RestrictionReason) = detail.copy(imageUrls = emptyList(), gate = reason)
        // 未识别负值的原文提示（网页端 DispMsg(html) 同款行为）；-2 非密码作品也走这里。
        // -3 且已登录是会话失效的瞬态（notifySessionInvalidIfNeeded 已触发自动重登），
        // 不弹原文避免打扰
        fun serverNotice(code: Int?, html: String?, passwordError: Boolean, unlockBlocked: Boolean): String? {
            if (code == null || code >= 0 || code == -4 || passwordError || unlockBlocked) return null
            if (code == ThumbnailResolver.RESULT_LOGIN_REQUIRED && loggedIn) return null
            return WorkDetailParser.extractUnlockBlockedMessage(html.orEmpty()).takeIf { it.isNotBlank() }
        }

        // 1) warning + R-18 显示关 → R-18 锁定（App 显示策略；服务端 append 其实
        //    对 R-18 内容放行，这是我们的产品选择：未开显示不展示 R-18 作品内容）
        if (detail.warning && !adultEnabled) {
            Log.d(TAG, "detail adult-locked work=${work.authorId}/${work.id}")
            return@apiCall gated(RestrictionReason.ADULT)
        }
        // 2) 匿名 + 任一门属性 → 登录门（关注/密码都要求先登录；实测服务端对匿名
        //    的 follower/TFollower 作品也返回登录提示）
        if (!loggedIn &&
            (detail.loginRequired || detail.followerGate || detail.twitterFollowerGate)
        ) {
            Log.d(TAG, "detail login-gate work=${work.authorId}/${work.id}")
            return@apiCall gated(RestrictionReason.LOGIN)
        }
        // 3) poipiku 内「こっそりフォロー」限定（class "Follower"）未关注 → 关注门。
        //    已关注则不拦，继续走密码/内容（此类作品常另设密码）
        if (detail.followerGate && !detail.followed) {
            Log.d(TAG, "detail follower-gate work=${work.authorId}/${work.id}")
            return@apiCall gated(RestrictionReason.FOLLOW)
        }
        // 4) Twitter 关注者限定（class "TFollower"）→ 引导网页（解锁在 Twitter 侧）
        if (detail.twitterFollowerGate) {
            Log.d(TAG, "detail twitter-follower-gate work=${work.authorId}/${work.id}")
            return@apiCall gated(RestrictionReason.FOLLOW_TWITTER)
        }
        // 5) 密码门 → 密码框（不设 gate；密码校验由服务端做：-2 错 / -4 未关注 / -3 需登录）
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
                // 追加图 HTML 含全部图片标签与正文，正则扫描放到 Default 上做
                val (appendUrls, novelText) = withContext(Dispatchers.Default) {
                    val urls = if (appendResp.result_num > 0) {
                        WorkDetailParser.extractImageUrls(appendResp.html)
                    } else {
                        emptyList()
                    }
                    urls to WorkDetailParser.extractNovelText(appendResp.html)
                }
                // 仅列表当前是占位图/空图才替换，判定见 ThumbnailResolver.backfillThumbnailUrl
                ThumbnailResolver.backfillThumbnailUrl(work.thumbnailUrl, detail.imageUrls + appendUrls)
                    ?.let { url -> thumbnailResolver.rememberThumb(enrichedWork(work, detail), url) }
                // -2 在密码作品上是"密码错误"；非密码作品上是未识别的拒绝，
                // 走服务端原文提示（不混标 passwordError）
                val passwordError = detail.passwordProtected && appendResp.result_num == -2
                val unlockBlocked = appendResp.result_num == -4
                val fullUrls = fullD?.await().orEmpty()
                val textOnly = novelText.isNotBlank() && appendUrls.isEmpty()
                val urls = if (textOnly) emptyList()
                else if (loggedIn) fullUrls.ifEmpty { appendUrls } else appendUrls
                Log.d(TAG, "detail warning urls=${urls.size} novel=${novelText.length} pwError=$passwordError blocked=$unlockBlocked work=${work.authorId}/${work.id} loggedIn=$loggedIn")
                rememberWorkPassword(work, password, appendResp.result_num)
                val gate = appendGate(appendResp.result_num, loggedIn)
                val notice = if (gate != null) null
                else serverNotice(appendResp.result_num, appendResp.html, passwordError, unlockBlocked)
                when {
                    gate != null -> detail.copy(
                        imageUrls = emptyList(),
                        gate = gate,
                        serverNotice = notice,
                        novelText = novelText,
                    )
                    unlockBlocked -> detail.copy(
                        imageUrls = emptyList(),
                        passwordError = false,
                        unlockBlocked = true,
                        unlockBlockedMessage = WorkDetailParser.extractUnlockBlockedMessage(appendResp.html),
                        novelText = novelText,
                    )
                    else -> detail.copy(
                        imageUrls = urls,
                        passwordError = passwordError,
                        serverNotice = notice,
                        novelText = novelText,
                    )
                }
            }
            Log.d(
                TAG,
                "detailTiming warning work=${work.authorId}/${work.id} " +
                    "html=${htmlMs}ms total=${elapsedMs(startedAt)}ms",
            )
            return@apiCall result
        }
        if (detail.r18 && !adultEnabled) {
            // R18 且未开启显示：与 warning 分支一致返回锁定态。
            // 此前静默跳过 append，导致纯文本等无首屏图作品落到"暂无图片"占位。
            // （登录/关注门已在上面提前返回，不会落到这里被误判成 R-18 锁定）
            Log.d(TAG, "detail adult-locked work=${work.authorId}/${work.id}")
            return@apiCall gated(RestrictionReason.ADULT)
        }
        // ---- 阶段一：HTML 已给出首屏需要的一切（标题/描述/标签/作者/主图）----
        // 门判定到这一步全部只依赖 HTML 与登录态，与 append 无关，所以此刻画出来的
        // 内容不会被后面的服务端拒绝码推翻（只有 class 漏判的兜底门会退回门卡，
        // 那种情况本来也得等服务端答复）。
        // 关键在于这行在限速等待之前：append 有全局限速（两次之间最短 12 秒），
        // 过去首屏要一起等它；现在首屏只等 HTML 这一个往返。
        onPartial?.let { callback ->
            DetailLoadPolicy.partialFromHtml(detail)?.let { partial -> callback(partial) }
        }
        val throttleStartedAt = System.nanoTime()
        thumbnailResolver.throttleAppend(force = password.isNotBlank())
        val throttleWaitMs = elapsedMs(throttleStartedAt)
        val appendStartedAt = System.nanoTime()
        val appendResp = runCatching {
            api.showAppendFile(work.authorId, work.id, password, 0, -1)
        }.getOrNull()
        val appendMs = elapsedMs(appendStartedAt)
        appendResp?.let { notifySessionInvalidIfNeeded(it.result_num) }
        // 追加图 HTML 含全部图片标签与正文，正则扫描放到 Default 上做
        val (appendUrls, novelText) = withContext(Dispatchers.Default) {
            val html = appendResp?.html.orEmpty()
            val urls = if (appendResp != null && appendResp.result_num > 0) {
                WorkDetailParser.extractImageUrls(html)
            } else {
                emptyList()
            }
            urls to WorkDetailParser.extractNovelText(html)
        }
        // 回填列表缩略图缓存：点开详情拿到真实图后列表立即显示。
        // 仅占位图/空图才替换——真实缩略图被追加图覆盖会让卡片换图并重新加载，判定见
        // ThumbnailResolver.backfillThumbnailUrl
        ThumbnailResolver.backfillThumbnailUrl(work.thumbnailUrl, detail.imageUrls + appendUrls)
            ?.let { url -> thumbnailResolver.rememberThumb(enrichedWork(work, detail), url) }
        // append 的 -2 只在密码作品上表示"密码错误"；非密码作品拿到 -2 视为未识别
        // 拒绝，走服务端原文提示
        val passwordError = detail.passwordProtected && appendResp?.result_num == -2
        val unlockBlocked = appendResp?.result_num == -4
        val gate = appendGate(appendResp?.result_num, loggedIn)
        val notice = if (gate != null) null
        else serverNotice(appendResp?.result_num, appendResp?.html, passwordError, unlockBlocked)
        Log.d(
            TAG,
            "detail normal append work=${work.authorId}/${work.id} result_num=${appendResp?.result_num} " +
                "gate=$gate r18=${detail.r18} html=${detail.imageUrls} appendUrls=$appendUrls",
        )
        // 详情页 HTML 提供第 1 张（主图），append 返回第 2 张起的追加图；
        // 合并时过滤 sign in/R-18 等占位图，只保留真实图
        val textOnly = novelText.isNotBlank() && appendUrls.isEmpty()
        val urls = when {
            // 纯文本作品：append 只返回 NovelSection 正文、不含任何图片，清空图片仅渲染正文
            textOnly -> emptyList()
            // 密码作品解锁失败（密码错误 -2 / 账号受限 -4）：保持锁页，
            // 避免显示 HTML 主图造成"已解锁"假象（追加图实际未解锁）
            detail.passwordProtected && (passwordError || unlockBlocked) -> emptyList()
            passwordError -> detail.imageUrls
            else -> ThumbnailResolver.mergeWorkImages(detail.imageUrls, appendUrls)
        }
        rememberWorkPassword(work, password, appendResp?.result_num ?: 0)
        // 慢开自证：html 是首屏实际等待，throttle 是过去连首屏一起等的限速等待，
        // append 是补追加图/正文的往返。三者分开记，才能判断下一次该优化哪一段
        Log.d(
            TAG,
            "detailTiming work=${work.authorId}/${work.id} html=${htmlMs}ms " +
                "throttle=${throttleWaitMs}ms append=${appendMs}ms total=${elapsedMs(startedAt)}ms",
        )
        when {
            // 服务端要求登录/键推关注/转推（class 判定漏判时的兜底）：门卡接管图区
            gate != null -> detail.copy(
                imageUrls = emptyList(),
                gate = gate,
                serverNotice = notice,
                novelText = novelText,
            )
            unlockBlocked -> detail.copy(
                imageUrls = emptyList(),
                passwordError = false,
                unlockBlocked = true,
                // unlockBlocked 为真就意味着 append 真的答了话（-4），此处 appendResp 必非空
                unlockBlockedMessage = WorkDetailParser.extractUnlockBlockedMessage(appendResp.html),
                novelText = novelText,
            )
            else -> detail.copy(
                imageUrls = urls,
                passwordError = passwordError,
                serverNotice = notice,
                novelText = novelText,
            )
        }
    }

    /** 单调时钟差值（毫秒）：只用于诊断计时，不受系统时间调整影响 */
    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

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

    /**
     * 全尺寸原图列表。未登录/未开 R-18 时服务端返回 error_code=-2、无 HTML——
     * 缩略图已在详情里可见，这里静默降级为空列表（UI 沿用缩略图），
     * 不给逐作品的"去登录"提示（用户反馈：太像强制登录引导）。
     */
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
     * append 拒绝码 → 门卡类型（与 poipiku 网页端行为对齐；逆向自 common.js）：
     * -3 需登录（仅匿名；登录态的 -3 是会话失效，由 [notifySessionInvalidIfNeeded]
     * 触发自动重登，不给门卡）/ -5 Twitter 关注者限定 / -20 需转推。
     * 返回 null 表示非门卡类（-2 密码错、-4 受限、成功码）。
     */
    private fun appendGate(code: Int?, loggedIn: Boolean): RestrictionReason? = when {
        code == null -> null
        code == ThumbnailResolver.RESULT_LOGIN_REQUIRED && !loggedIn -> RestrictionReason.LOGIN
        code == RESULT_TWITTER_FOLLOWER_LIMIT -> RestrictionReason.FOLLOW_TWITTER
        code == RESULT_RETWEET_REQUIRED -> RestrictionReason.RETWEET
        else -> null
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

    /**
     * 屏蔽/解除屏蔽用户（POST /f/UpdateBlockF.jsp，UID=当前用户、IID=目标用户、CHK=1/0）。
     * 未登录直接返回 [BlockResult.NotLoggedIn]，不发请求。
     * 服务端在屏蔽成功时会同时解除对该用户的关注，调用方需据此同步关注态。
     * 成功后同步本地屏蔽名单（[BlockListRepository]），供各列表页即时过滤与
     * 屏蔽列表页展示——官方 BlockListF 有缓存延迟，不能只依赖服务端列表。
     * [name]/[avatar] 用于本地名单展示，可空。
     */
    suspend fun updateBlock(
        targetUserId: Long,
        blocked: Boolean,
        name: String = "",
        avatarUrl: String? = null,
    ): BlockResult = try {
        val uid = authRepository.currentUserId()
        if (uid == null) return BlockResult.NotLoggedIn
        val resp = api.updateBlockUser(uid, targetUserId, if (blocked) 1 else 0)
        Log.d(TAG, "updateBlock target=$targetUserId uid=$uid chk=$blocked result=${resp.result}")
        when (resp.result) {
            1 -> {
                blockListRepository.add(targetUserId, name, avatarUrl)
                BlockResult.Blocked
            }
            2 -> {
                blockListRepository.remove(targetUserId)
                BlockResult.Unblocked
            }
            else -> BlockResult.Failure()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        BlockResult.Failure(e.message.orEmpty())
    }

    private companion object {
        const val TAG = "PikuDiag"

        /** append result_num：Twitter 关注者限定（网页端弹 TwitterFollowerLimitInfoDlg） */
        const val RESULT_TWITTER_FOLLOWER_LIMIT = -5

        /** append result_num：需转推（网页端弹转推确认框，转推后重试即可放行） */
        const val RESULT_RETWEET_REQUIRED = -20
    }
}