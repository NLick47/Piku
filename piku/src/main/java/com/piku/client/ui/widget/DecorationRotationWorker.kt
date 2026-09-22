package com.piku.client.ui.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 装饰轮播：每个周期把画框推进到白名单的下一张并重渲染 widget。
 *
 * 调度策略（[schedule] 维护）：
 * - 白名单 ≥ 2 张 → 周期任务，按添加时间循环换画；
 * - ≤ 1 张 → 取消调度（一张没有"轮"可言，也省电）。
 *
 * 索引只在 widget 实际显示的"画框模式"下有意义；画廊模式（宽尺寸平铺全部）
 * 不看索引，推进对它无害。白名单变化后索引按模数自动收敛，无需清理。
 */
class DecorationRotationWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            WidgetEntryPoint::class.java,
        )
        // 总开关关闭期间不轮换；调度本身会在下次 syncSchedule 时被取消
        if (!entryPoint.settingsRepository().decorationEnabled.value) return Result.success()
        val items = entryPoint.database().decorationDao().getAllOnce()
        if (items.size < 2) {
            syncSchedule(context)
            return Result.success()
        }
        val next = (DecorationState.readRotationIndex(context) + 1) % items.size
        DecorationState.writeRotationIndex(context, next)
        DecorationWidget.updateAll(context)
        return Result.success()
    }

    companion object {
        /** 轮换间隔：装饰要"静"，一小时翻一次画足够有惊喜感 */
        private const val ROTATION_HOURS = 1L
        private const val WORK_NAME = "decoration_rotation"

        /** [schedule] 的挂起版本：Worker 内部直接 await，避免 doWork 返回后进程被回收导致调度丢失 */
        suspend fun syncSchedule(context: Context) {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            )
            val workManager = WorkManager.getInstance(context)
            val enabled = entryPoint.settingsRepository().decorationEnabled.value
            val count = entryPoint.database().decorationDao().count()
            if (enabled && count >= 2) {
                val request = PeriodicWorkRequestBuilder<DecorationRotationWorker>(
                    ROTATION_HOURS, TimeUnit.HOURS,
                ).build()
                workManager.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            } else {
                workManager.cancelUniqueWork(WORK_NAME)
            }
        }

        /**
         * 使调度与「总开关 × 白名单现状」一致：开启且 ≥2 张才有周期任务，
         * 关闭或不足 2 张一律取消。幂等，白名单增删与开关切换后都会调用。
         */
        fun schedule(context: Context) {
            CoroutineScope(Dispatchers.IO).launch { syncSchedule(context) }
        }
    }
}
