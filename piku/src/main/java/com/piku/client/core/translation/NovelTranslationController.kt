package com.piku.client.core.translation

import com.piku.client.core.model.WorkDetail
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 正文分块流式翻译的编排：一次只跑一路流，按宿主给的 key 保留已译内容。
 * 单篇用 key 0，分章用章号；显示原文还是译文由宿主决定，这里只管内容与进度。
 */
class NovelTranslationController(
    private val hasModel: () -> Boolean,
    private val stream: (WorkDetail, String) -> Flow<NovelStreamEvent>,
    private val scope: CoroutineScope,
) {
    data class Part(
        val translated: String? = null,
        /** 流式期间未译的剩余原文，与 [translated] 拼接即为完整展示文本 */
        val remainder: String? = null,
        val progress: Int? = null,
        /** 产出这份译文的原文；正文换了（解锁、重载）就不再认它 */
        val source: String? = null,
    )

    data class State(
        val parts: Map<Int, Part> = emptyMap(),
        /** 正在跑流的 key；null = 空闲 */
        val running: Int? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _failed = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    /** true = 整段一段都没译成（接口不可用），false = 只有部分块失败 */
    val failed: SharedFlow<Boolean> = _failed.asSharedFlow()

    private var job: Job? = null
    private var generation = 0

    fun hasText(key: Int, source: String? = null): Boolean {
        val part = _state.value.parts[key] ?: return false
        if (part.translated == null) return false
        return source == null || part.source == null || part.source == source
    }

    /** 整段是否已译完；被取消或失败打断、还剩未译原文的半译状态不算 */
    fun isComplete(key: Int, source: String? = null): Boolean {
        val part = _state.value.parts[key] ?: return false
        val fresh = source == null || part.source == null || part.source == source
        return part.translated != null && part.remainder == null && part.progress == null && fresh
    }

    /** 展示文本 = 已译前缀 + 未译剩余；无译文或原文已变则返回 null，宿主据此回落原文 */
    fun displayText(key: Int, source: String? = null): String? {
        val part = _state.value.parts[key] ?: return null
        val translated = part.translated ?: return null
        if (source != null && part.source != null && part.source != source) return null
        return translated + (part.remainder ?: "")
    }

    /** 预置译文，如详情页已翻好的短正文；记下来源，正文换了就不再认它 */
    fun seed(key: Int, translated: String?, source: String?) {
        if (translated.isNullOrBlank() || _state.value.parts[key] != null) return
        _state.update {
            it.copy(parts = it.parts + (key to Part(translated = translated, source = source)))
        }
    }

    fun start(detail: WorkDetail, key: Int, source: String) {
        Log.d(
            "PikuDiag",
            "novel start key=$key src=${source.length} hasModel=${hasModel()} " +
                "running=${_state.value.running}",
        )
        if (source.isBlank() || !hasModel()) {
            Log.d("PikuDiag", "novel start skipped key=$key (blankSource=${source.isBlank()})")
            return
        }
        cancel()
        val gen = ++generation
        _state.update { it.copy(running = key) }
        job = scope.launch {
            try {
                stream(detail, source)
                    .collect { event ->
                        when (event) {
                            is NovelStreamEvent.Progress -> Log.d(
                                "PikuDiag",
                                "novel progress key=$key ${event.doneChunks}/${event.totalChunks} " +
                                    "len=${event.translatedSoFar.length}",
                            )

                            is NovelStreamEvent.Completed -> Log.d(
                                "PikuDiag",
                                "novel completed key=$key failed=${event.failedCount}/${event.totalChunks} " +
                                    "aborted=${event.aborted} len=${event.translatedSoFar.length}",
                            )
                        }
                        when (event) {
                            is NovelStreamEvent.Progress -> _state.update { state ->
                                state.merge(
                                    key,
                                    Part(
                                        translated = event.translatedSoFar,
                                        remainder = source.substring(
                                            event.consumedOffset.coerceIn(0, source.length),
                                        ),
                                        progress = event.doneChunks * 100 / event.totalChunks.coerceAtLeast(1),
                                        source = source,
                                    ),
                                )
                            }

                            is NovelStreamEvent.Completed -> {
                                // 整段（或提前中止时已处理的部分）一块都没成功：视为完全失败，
                                // 不留下与原文全等的「译文」，否则 chip 会停在「原文」且不再重试
                                val allFailed = event.aborted ||
                                    (event.totalChunks > 0 && event.failedCount >= event.totalChunks)
                                val output = event.translatedSoFar.ifBlank { null }
                                    ?.takeUnless { allFailed || it == source }
                                _state.update {
                                    it.merge(key, Part(translated = output, source = source))
                                }
                                if (event.failedCount > 0) {
                                    Log.d("PikuDiag", "novel failed emit key=$key allFailed=$allFailed")
                                    _failed.tryEmit(allFailed)
                                }
                            }
                        }
                    }
            } catch (e: CancellationException) {
                Log.d("PikuDiag", "novel cancelled key=$key gen=$gen")
                throw e
            } catch (e: Exception) {
                Log.d("PikuDiag", "novel error key=$key ${e::class.simpleName}: ${e.message}")
                _state.update { it.clearProgress(key) }
            } finally {
                if (gen == generation) {
                    _state.update { if (it.running == key) it.copy(running = null) else it }
                }
            }
        }
    }

    fun cancel() {
        val running = _state.value.running
        job?.cancel()
        job = null
        generation++
        _state.update { state ->
            state.copy(running = null, parts = running?.let { state.clearProgress(it).parts } ?: state.parts)
        }
    }

    private fun State.merge(key: Int, part: Part): State = copy(parts = parts + (key to part))

    private fun State.clearProgress(key: Int): State {
        val part = parts[key] ?: return this
        // 只清进度：已译前缀仍要配上剩余原文，否则这条会被截成半章
        return copy(parts = parts + (key to part.copy(progress = null)))
    }
}
