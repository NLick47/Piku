package com.piku.client.core.diag

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

/**
 * 异常记录：Java 崩溃由未捕获处理器落盘，原生崩溃 / ANR / 被杀由系统退出记录搬进来，
 * 最多留 [MAX_ENTRIES] 条。内容是给作者看的，不参与任何业务逻辑。
 */
@Singleton
class CrashLogStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val file = File(context.filesDir, CRASH_FILE)
    private val marker = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var installed = false

    /** 装在最早处（Application.onCreate）：这之后的任何崩溃都该留得下痕迹 */
    fun install() {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(javaEntry(error, thread.name, System.currentTimeMillis())) }
                .onFailure { Log.w(TAG, "崩溃记录写入失败", it) }
            // 记完照旧交给原来的处理器：系统仍要按正常流程收尾并打印 logcat
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(KILL_EXIT_CODE)
            }
        }
    }

    /**
     * 搬运系统的进程退出记录：只要 Java 处理器看不到的那几类（原生崩溃、ANR、
     * 内存不足被杀）。Android 11 起才有这个接口，更低版本只有 Java 崩溃可记。
     */
    fun importRecentExits() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val manager = context.getSystemService(ActivityManager::class.java) ?: return
        val since = marker.getLong(KEY_IMPORT_AT, 0L)
        val exits = runCatching {
            manager.getHistoricalProcessExitReasons(context.packageName, 0, EXIT_QUERY_LIMIT)
        }.getOrNull().orEmpty()
        if (exits.isEmpty()) return
        exits.asSequence()
            .filter { it.timestamp > since }
            .sortedBy { it.timestamp }
            .mapNotNull { info ->
                exitKindForReason(info.reason)?.let { kind ->
                    exitEntry(kind, info.timestamp, info.description, readTrace(info))
                }
            }
            .forEach { record(it) }
        marker.edit().putLong(KEY_IMPORT_AT, exits.maxOf { it.timestamp }).apply()
    }

    fun sectionText(): String = formatCrashSection(readEntries())

    fun clear() {
        runCatching { file.delete() }
        // 标记推到此刻：已被清掉的那些退出记录不该下次冷启动又冒出来
        marker.edit().putLong(KEY_IMPORT_AT, System.currentTimeMillis()).apply()
    }

    @Synchronized
    private fun readEntries(): List<CrashEntry> =
        runCatching { decodeEntries(file.readText()) }.getOrDefault(emptyList())

    @Synchronized
    private fun record(entry: CrashEntry) {
        file.writeText(encodeEntries(mergeEntries(readEntries(), entry)))
    }

    private fun readTrace(info: ApplicationExitInfo): String = runCatching {
        info.traceInputStream?.use { stream -> String(stream.readBytes(), Charsets.UTF_8) }.orEmpty()
    }.getOrDefault("")

    private companion object {
        const val CRASH_FILE = "crash_log.txt"
        const val PREFS_NAME = "crash_log"
        const val KEY_IMPORT_AT = "exit_import_at"

        /** 一次最多看系统记录的多少条进程退出 */
        const val EXIT_QUERY_LIMIT = 10
        const val KILL_EXIT_CODE = 10
        const val TAG = "PikuDiag"
    }
}

/**
 * 退出原因 → 记录类型。Java 崩溃（REASON_CRASH）不在这里：未捕获处理器已经记过一份，
 * 再来一条只是重复；用户主动划掉、正常退出的那些也都不算异常。
 */
internal fun exitKindForReason(reason: Int): CrashKind? = when (reason) {
    ApplicationExitInfo.REASON_CRASH_NATIVE -> CrashKind.NATIVE
    ApplicationExitInfo.REASON_ANR -> CrashKind.ANR
    ApplicationExitInfo.REASON_LOW_MEMORY -> CrashKind.LOW_MEMORY
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> CrashKind.INIT_FAILURE
    ApplicationExitInfo.REASON_SIGNALED -> CrashKind.SIGNALED
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> CrashKind.RESOURCE
    else -> null
}

/** 报告头部的设备信息：ABI 尤其要紧（只打了 arm64 的 so，32 位机上会连带崩） */
fun deviceInfo(appVersion: String): DeviceInfo = DeviceInfo(
    appVersion = appVersion,
    android = "${Build.VERSION.RELEASE}（SDK ${Build.VERSION.SDK_INT}）",
    model = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "未知机型" },
    rom = Build.DISPLAY?.takeIf { it.isNotBlank() } ?: "未知版本",
    abis = Build.SUPPORTED_ABIS?.joinToString(", ")?.takeIf { it.isNotBlank() } ?: "未知",
)
