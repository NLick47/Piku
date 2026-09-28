package com.piku.client.data.remote

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NetworkDiagnostics(
    private val runtime: NetworkRuntime = NetworkRuntime(),
) {

    enum class Level { INFO, WARN }

    data class Entry(
        val atMillis: Long,
        val level: Level,
        val message: String,
        /** 连续重复次数：断网时同一句话会被每个请求重复写入，靠计数而不是刷屏表达 */
        val repeat: Int = 1,
    )

    /** 最近一次成功建连的协议（h2 / http/1.1），用于确认 ALPN 是否真的生效 */
    @Volatile
    var lastProtocol: String? = null
        private set

    private val lock = Any()
    private val entries = ArrayDeque<Entry>()

    fun info(message: String) = record(Level.INFO, message)

    fun warn(message: String) = record(Level.WARN, message)

    fun recordConnectionProtocol(protocol: String) {
        lastProtocol = protocol
    }

    /** 最近事件，最新的在前 */
    fun snapshot(): List<Entry> = synchronized(lock) { entries.toList() }.asReversed()

    fun clear() {
        synchronized(lock) { entries.clear() }
        lastProtocol = null
    }

    /** 事件行（不含标题），供诊断报告复用 */
    fun eventLines(): List<String> = snapshot().map { entry ->
        val mark = if (entry.level == Level.WARN) "!" else " "
        val repeat = if (entry.repeat > 1) " (×${entry.repeat})" else ""
        "$mark[${formatStamp(entry.atMillis)}] ${entry.message}$repeat"
    }

    private fun formatStamp(millis: Long): String =
        SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date(millis))

    private fun record(level: Level, message: String) {
        synchronized(lock) {
            val last = entries.lastOrNull()
            if (last != null && last.level == level && last.message == message) {
                // 连续重复只累加计数：否则 200 条缓冲会被同一句话刷满，
                // 把真正有用的事件（降级原因、兜底）挤出窗口
                entries.removeLast()
                entries.addLast(last.copy(repeat = last.repeat + 1))
            } else {
                if (entries.size >= MAX_ENTRIES) entries.removeFirst()
                entries.addLast(Entry(runtime.now(), level, message))
            }
        }
        if (level == Level.WARN) Log.w(TAG, message) else Log.d(TAG, message)
    }

    companion object {
        const val TAG = "PikuDiag"
        const val MAX_ENTRIES = 200
    }
}
