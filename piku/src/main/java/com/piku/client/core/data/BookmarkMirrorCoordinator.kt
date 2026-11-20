package com.piku.client.core.data

import android.util.Log
import com.piku.client.core.model.Work
import com.piku.client.core.model.key
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkMirrorCoordinator @Inject constructor(
    private val mirrors: BookmarkMirrorRepository,
) {

    private data class Task(val work: Work, val favorited: Boolean) {
        val id: String get() = "${work.source}:${work.key.workId}"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tasks = Channel<Task>(Channel.UNLIMITED)

    private val latest = ConcurrentHashMap<String, Task>()

    init {
        scope.launch {
            for (task in tasks) {
                if (latest[task.id] !== task) continue
                latest.remove(task.id, task)
                try {
                    mirrors.syncOne(task.work, task.favorited).onFailure {
                        Log.w(
                            TAG,
                            "mirror failed: source=${task.work.source} work=${task.work.id} " +
                                "favorited=${task.favorited}",
                            it,
                        )
                    }
                } catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    Log.e(TAG, "mirror worker task cancelled unexpectedly", e)
                } catch (e: Exception) {
                    Log.e(TAG, "mirror worker task failed", e)
                }
            }
        }
    }

    fun onFavoriteChanged(work: Work, favorited: Boolean) {
        if (mirrors.source(work.source) == null) return
        val task = Task(work, favorited)
        latest[task.id] = task
        val sent = tasks.trySend(task)
        if (sent.isFailure) {
            Log.w(TAG, "mirror task dropped: source=${work.source} work=${work.id}")
        }
    }

    private companion object {
        const val TAG = "BookmarkMirror"
    }
}
