package com.piku.client.core.translation

import android.util.Log
import com.piku.client.core.model.WorkDetail
import com.piku.client.core.model.mergeTranslatedFields
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 详情页短字段翻译的唯一编排：poipiku 与各内容源详情页共用。
 * 宿主只提供状态读写与请求执行，队列、失败策略、译文合并、自动补翻都在这里。
 */
class DetailTranslationController(
    private val translate: suspend (WorkDetail, Request) -> TranslationOutcome,
    private val config: () -> Config,
    private val scope: CoroutineScope,
    private val read: () -> State,
    private val write: (transform: (State) -> State) -> Unit,
    private val onFailed: (Failure) -> Unit,
    autoEnabled: Flow<Boolean>,
    modelAvailability: Flow<RoleModelAvailability>,
    /** 宿主特有的内容门禁，如密码占位页没有可翻内容 */
    private val canTranslate: (WorkDetail) -> Boolean = { true },
) {
    data class Config(val autoEnabled: Boolean, val hasKey: Boolean)

    data class State(
        val detail: WorkDetail? = null,
        val translating: Boolean = false,
        val showTranslationAll: Boolean = false,
        val fetchingNovelText: Boolean = false,
    )

    data class Request(
        val origin: Origin,
        val includeLongNovel: Boolean = false,
        val forcedEntry: ModelEntry? = null,
        val forceShow: Boolean = false,
        /** 用户主动触发的补翻（打开开关）：配置不可用要出声，且不受自动节流限制 */
        val userInitiated: Boolean = false,
    )

    enum class Origin { AUTO, MANUAL }
    enum class Failure { NO_MODEL, REQUEST }

    private var pending: Request? = null
    /** 每次起跑自增：被手动意图抢占的旧任务结果与收尾都作废 */
    private var generation = 0
    /** 同一份作品内容只自动尝试一次；解锁换了内容才允许再来 */
    private var autoAttemptFor: WorkDetail? = null

    init {
        // 首发射是开关当前值（不是用户操作），跳过；页面加载由宿主自行发 AUTO 请求
        scope.launch {
            autoEnabled.drop(1).collect { enabled ->
                if (enabled && read().detail?.translated == null) {
                    request(Request(Origin.AUTO, userInitiated = true))
                }
            }
        }
        // 冷启动目录晚到时补翻：此前 hasKey 为 false 的请求不会留下节流标记
        scope.launch {
            modelAvailability.collect { avail ->
                if ((avail.text || avail.novel) && config().autoEnabled &&
                    read().detail?.translated == null
                ) {
                    request(Request(Origin.AUTO))
                }
            }
        }
    }

    fun request(request: Request) {
        val detail = read().detail ?: return
        if (!canTranslate(detail)) return
        if (request.origin == Origin.AUTO && !config().autoEnabled) return
        if (read().translating) {
            if (request.origin == Origin.MANUAL) {
                // 手动意图不排队：真正起跑时由新 generation 让在途自动任务的结果作废。
                // 这里不动 generation——若本次被配置门禁拦下，旧任务仍该正常收尾。
                pending = null
                Log.d("PikuDiag", "detail translate preempted by manual request")
            } else {
                // 自动请求只留最近一次，当前轮结束后补跑
                pending = if (pending?.origin == Origin.MANUAL) pending else request
                Log.d("PikuDiag", "detail translate queued origin=${request.origin} (busy)")
                return
            }
        }
        if (!config().hasKey) {
            Log.d("PikuDiag", "detail translate skip origin=${request.origin}: no usable model")
            if (request.origin == Origin.MANUAL || request.userInitiated) {
                onFailed(Failure.NO_MODEL)
            }
            return
        }
        if (request.origin == Origin.AUTO && !request.userInitiated &&
            detail.copy(translated = null) == autoAttemptFor
        ) return
        autoAttemptFor = detail.copy(translated = null)
        run(request, detail)
    }

    private fun run(request: Request, detail: WorkDetail) {
        val gen = ++generation
        write { it.copy(translating = true, fetchingNovelText = request.includeLongNovel) }
        scope.launch {
            try {
                val outcome = try {
                    translate(detail, request)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d("PikuDiag", "detail translate fail: ${e::class.simpleName}: ${e.message}")
                    null
                }
                if (gen != generation) return@launch
                val failed = outcome == null || outcome.failed
                val fields = outcome?.takeUnless { it.failed }?.fields
                write { state ->
                    // 期间可能已重新加载/解锁成新 detail，按当前 detail 回填
                    val current = state.detail ?: return@write state
                    val merged = mergeTranslatedFields(current.translated, fields)
                    val show = when {
                        merged == null -> state.showTranslationAll
                        request.forceShow || request.origin == Origin.MANUAL -> true
                        // 自动路径只在首次拿到译文时翻面，不覆盖用户切回原文的选择
                        current.translated == null || state.showTranslationAll -> true
                        else -> state.showTranslationAll
                    }
                    state.copy(
                        // 本次没有可用结果时维持原状，绝不能用一次失败抹掉已上屏的译文
                        detail = if (merged != null) current.copy(translated = merged) else current,
                        showTranslationAll = show,
                    )
                }
                if (failed && request.origin == Origin.MANUAL) onFailed(Failure.REQUEST)
                Log.d(
                    "PikuDiag",
                    "detail translate done origin=${request.origin} failed=$failed fields=${fields != null}",
                )
            } finally {
                if (gen == generation) {
                    write { it.copy(translating = false, fetchingNovelText = false) }
                    if (isActive) {
                        pending?.let { queued ->
                            pending = null
                            request(queued)
                        }
                    }
                }
            }
        }
    }
}
