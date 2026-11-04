package com.piku.client.core.data

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.piku.client.R
import com.piku.client.core.model.Work
import com.piku.client.core.model.WorkSource
import com.piku.client.core.source.SourceFavoriteMirror
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 收藏动作的远程镜像调度器。
 *
 * 本地收藏先成功（星标立即回显），镜像在独立协程里异步执行；网络失败不阻塞本地操作，
 * 只提示一次，具体补齐交给收藏页的手动同步。
 *
 * 用单消费者 Channel 而不是"每个任务一个协程 + Mutex"：投递顺序就是执行顺序。
 * 快速连点收藏-取消时，最后一次本地状态一定最后落到远端；否则 IO 调度可能把两个
 * 协程乱序执行，远端最终停在相反状态。
 */
@Singleton
class FavoriteMirrorService @Inject constructor(
    mirrors: Set<@JvmSuppressWildcards SourceFavoriteMirror>,
    @ApplicationContext private val context: Context,
) {

    private data class Task(
        val work: Work,
        val favorited: Boolean,
        /** 提交时的账号标识；执行时不一致就丢弃，避免换号串写 */
        val accountKey: String?,
    )

    private val bySource: Map<WorkSource, SourceFavoriteMirror> =
        mirrors.associateBy { it.source }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tasks = Channel<Task>(Channel.UNLIMITED)

    /** 只被单消费者写；批量失败时不刷屏 */
    private var lastFailureToastAt = 0L

    init {
        scope.launch {
            for (task in tasks) {
                try {
                    process(task)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 单个任务把 worker 打死会静默停掉后续所有镜像
                    Log.e(TAG, "mirror worker task failed", e)
                }
            }
        }
    }

    fun onFavoriteChanged(work: Work, favorited: Boolean) {
        val mirror = bySource[work.source] ?: return
        val sent = tasks.trySend(Task(work, favorited, mirror.accountKey()))
        if (sent.isFailure) {
            Log.w(TAG, "mirror task dropped: source=${work.source} work=${work.id}")
        }
    }

    private suspend fun process(task: Task) {
        val mirror = bySource[task.work.source] ?: return
        if (mirror.accountKey() != task.accountKey) {
            // 提交后换了账号/登出：这条任务属于旧账号，不能落到当前账号
            Log.d(
                TAG,
                "mirror task skipped: account changed, source=${task.work.source} work=${task.work.id}",
            )
            return
        }
        val result = try {
            mirror.mirror(task.work, task.favorited)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        if (result.isFailure) {
            Log.w(
                TAG,
                "mirror failed: source=${task.work.source} work=${task.work.id} " +
                    "favorited=${task.favorited}",
                result.exceptionOrNull(),
            )
            val now = System.currentTimeMillis()
            if (now - lastFailureToastAt >= FAILURE_TOAST_GAP_MS) {
                lastFailureToastAt = now
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.favorite_mirror_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private companion object {
        const val TAG = "FavoriteMirror"
        const val FAILURE_TOAST_GAP_MS = 5_000L
    }
}
