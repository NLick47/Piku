package com.piku.client.data.remote

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageDiagnostics(private val runtime: NetworkRuntime = NetworkRuntime()) {

    enum class Outcome(val label: String) {
        OK("成功"),
        HTTP_ERROR("HTTP 错误"),
        CONNECT_FAILED("连接失败（IP 不通或被拦）"),
        HANDSHAKE_FAILED("握手/连接被重置（常见于 SNI 阻断）"),
        TRANSFER_FAILED("传输中断"),
        TIMEOUT("超时（含整体超时）"),
        CANCELLED("取消（滑走或整体超时）"),
    }

    data class Attempt(
        val atMillis: Long,
        val host: String,
        val path: String,
        val outcome: Outcome,
        val detail: String,
        val elapsedMs: Long,
        val relay: Boolean,
    )

    private val lock = Any()
    private val attempts = ArrayDeque<Attempt>()

    fun recordAttempt(
        host: String,
        path: String,
        outcome: Outcome,
        detail: String,
        elapsedMs: Long,
        relay: Boolean,
    ) {
        synchronized(lock) {
            if (attempts.size >= MAX_ATTEMPTS) attempts.removeFirst()
            attempts.addLast(
                Attempt(
                    atMillis = runtime.now(),
                    host = host,
                    path = tail(path),
                    outcome = outcome,
                    detail = detail,
                    elapsedMs = elapsedMs,
                    relay = relay,
                ),
            )
        }
    }

    fun clear() {
        synchronized(lock) { attempts.clear() }
    }

    /** 诊断报告用：计数、线路分布与最近失败明细 */
    fun summary(): List<String> = synchronized(lock) {
        val all = attempts.toList()
        val lines = mutableListOf<String>()
        if (all.isEmpty()) {
            lines += "本次进程还没有图片网络请求"
            return lines
        }
        val ok = all.count { it.outcome == Outcome.OK }
        val cancelled = all.count { it.outcome == Outcome.CANCELLED }
        lines += "请求 ${all.size} 次：成功 $ok  失败 ${all.size - ok - cancelled}  取消 $cancelled"
        Outcome.entries
            .filter { it != Outcome.OK && it != Outcome.CANCELLED }
            .forEach { outcome ->
                val count = all.count { it.outcome == outcome }
                if (count > 0) lines += "  ${outcome.label}  $count"
            }
        val relayCount = all.count { it.relay }
        val perRelay = all.filter { it.relay }.groupingBy { it.host }.eachCount()
            .entries.joinToString(" / ") { "${it.key} ${it.value}" }
        lines += "线路：直连 ${all.size - relayCount}  中转 $relayCount" +
            if (relayCount > 0) "（$perRelay）" else ""
        val failures = all
            .filter { it.outcome != Outcome.OK && it.outcome != Outcome.CANCELLED }
            .takeLast(MAX_FAILURES)
        if (failures.isNotEmpty()) {
            lines += "最近失败（最多 $MAX_FAILURES 条，最新在后）:"
            failures.forEach { attempt ->
                val line = if (attempt.relay) "中转 ${attempt.host}" else "直连 ${attempt.host}"
                val detail = attempt.detail.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
                lines += "  [${stamp(attempt.atMillis)}] $line${attempt.path}  " +
                    "${attempt.outcome.label}  ${attempt.elapsedMs}ms$detail"
            }
        }
        return lines
    }

    private fun tail(path: String): String =
        if (path.length <= MAX_PATH_CHARS) path else "…" + path.takeLast(MAX_PATH_CHARS)

    private fun stamp(millis: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(millis))

    private companion object {
        /** 环形缓冲：够看清最近一轮首页图片的情况 */
        const val MAX_ATTEMPTS = 60
        const val MAX_FAILURES = 10
        const val MAX_PATH_CHARS = 40
    }
}
