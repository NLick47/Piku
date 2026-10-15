package com.piku.client.core.diag

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 每条异常记录保留的明细长度上限：用户要把它贴进聊天窗口，太长贴不动 */
const val MAX_DETAIL_CHARS = 2_000

/** 最多留几条：超出时丢最旧的，宁可少留也不让报告长到没人看 */
const val MAX_ENTRIES = 3

internal const val CRASH_SECTION_TITLE = "—— 异常记录 ——"

/**
 * 一条异常记录。两个来源：App 自己的未捕获处理器（Java 崩溃），
 * 与系统的进程退出记录（原生崩溃 / ANR / 被杀，见 [CrashLogStore]）。
 */
data class CrashEntry(
    val kind: CrashKind,
    val timeMillis: Long,
    /** 单行摘要：异常类名 + 消息，或系统给的退出描述 */
    val headline: String,
    /** 多行明细：栈或 tombstone 摘要，已按 [MAX_DETAIL_CHARS] 截断 */
    val detail: String,
)

enum class CrashKind(val label: String) {
    JAVA("未捕获异常"),
    NATIVE("原生崩溃"),
    ANR("无响应 (ANR)"),
    LOW_MEMORY("内存不足被杀"),
    INIT_FAILURE("启动失败"),
    SIGNALED("被信号终止"),
    RESOURCE("资源占用过高被杀"),
}

/** 报告头部的设备信息；只取 Build 里的公开字段，不含账号与作品数据 */
data class DeviceInfo(
    val appVersion: String,
    val android: String,
    val model: String,
    val rom: String,
    val abis: String,
)

/**
 * 诊断报告 = 报告头 + 异常段 + 网络段。正文刻意只用中文：用户不读它，只是把它贴出来，
 * 两份内容各自由 [formatCrashSection] 与 NetworkDiagnosis 生成。
 */
fun buildDiagnosticsReport(
    device: DeviceInfo,
    stamp: String,
    crashSection: String,
    networkSection: String,
): String = buildString {
    appendLine("Piku ${device.appVersion} 诊断报告")
    appendLine("生成时间 $stamp")
    appendLine("设备 ${device.model} / Android ${device.android}")
    appendLine("系统 ${device.rom} / ${device.abis}")
    appendLine()
    appendLine(crashSection)
    appendLine()
    append(networkSection)
}

fun formatCrashSection(entries: List<CrashEntry>): String {
    val body = if (entries.isEmpty()) {
        "本机暂无异常记录"
    } else {
        entries.joinToString("\n\n") { formatEntry(it) }
    }
    return "$CRASH_SECTION_TITLE\n$body"
}

fun formatEntry(entry: CrashEntry): String =
    "${reportStamp(entry.timeMillis)}  ${entry.kind.label}\n  ${entry.headline}\n${entry.detail}"

/** 报告里的时间戳：贴出去可能几天后才被读到，带上完整日期 */
fun reportStamp(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))

/** 未捕获异常 → 记录。栈按 [MAX_DETAIL_CHARS] 截断，保留最上面的帧（那里才是现场） */
fun javaEntry(error: Throwable, threadName: String, nowMillis: Long): CrashEntry {
    val message = error.message?.trim()?.takeIf { it.isNotEmpty() }?.let { ": $it" }.orEmpty()
    return CrashEntry(
        kind = CrashKind.JAVA,
        timeMillis = nowMillis,
        headline = singleLine(error.javaClass.name + message),
        detail = truncate("线程 $threadName\n${error.stackTraceToString()}"),
    )
}

/** 系统退出记录 → 记录。[description] 是系统给的短摘要（如 SIGSEGV），可空 */
fun exitEntry(kind: CrashKind, timeMillis: Long, description: String?, trace: String): CrashEntry {
    val summary = description?.trim()?.takeIf { it.isNotEmpty() }?.let { ": ${singleLine(it)}" }.orEmpty()
    return CrashEntry(
        kind = kind,
        timeMillis = timeMillis,
        headline = kind.label + summary,
        detail = truncate(readableRuns(trace), MAX_DETAIL_CHARS),
    )
}

/**
 * 只留可读片段：原生崩溃的 trace 是 debuggerd 的 protobuf tombstone，按字符直接存下来
 * 是乱码，还会把 2000 字的上限浪费在二进制上（实测一份 tombstone 五十余万字，栈帧全被截掉）。
 * 换行与制表符保留、片段之间补一个换行，纯文本的 ANR trace 因此原样通过。
 */
fun readableRuns(text: String, minRun: Int = 6): String {
    val out = StringBuilder(text.length / 8)
    val run = StringBuilder()
    fun flush() {
        if (run.length >= minRun) {
            // 二进制里丢掉的分隔符不能变成粘连：一个片段一行才扫得出库名与符号
            if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
            out.append(run)
        }
        run.setLength(0)
    }
    text.forEach { ch ->
        if (ch == '\n' || ch == '\t' || (ch.code in 32..126)) run.append(ch) else flush()
    }
    flush()
    return out.toString()
}

fun truncate(text: String, maxChars: Int = MAX_DETAIL_CHARS): String =
    if (text.length <= maxChars) text
    else text.take(maxChars) + "\n… 已截断 ${text.length - maxChars} 字"

/** 新记录插到最前，超出 [max] 条丢最旧的 */
fun mergeEntries(existing: List<CrashEntry>, incoming: CrashEntry, max: Int = MAX_ENTRIES): List<CrashEntry> =
    (listOf(incoming) + existing).take(max)

private const val BLOCK_HEADER = "### PikuCrash v1"
private const val BLOCK_END = "@@end@@"

/**
 * 落盘格式：一条记录一个块，块头一行（类型|时间|摘要），明细跟在后面，末行哨兵。
 * 崩在写盘中途只会坏掉最后一块，[decodeEntries] 丢掉它、前面的记录照读。
 */
fun encodeEntries(entries: List<CrashEntry>): String = buildString {
    entries.forEach { entry ->
        appendLine(BLOCK_HEADER)
        appendLine("${entry.kind.name}|${entry.timeMillis}|${singleLine(entry.headline)}")
        appendLine(entry.detail)
        appendLine(BLOCK_END)
    }
}

fun decodeEntries(text: String): List<CrashEntry> =
    text.split(BLOCK_END).mapNotNull { parseBlock(it.trim('\n')) }

private fun parseBlock(block: String): CrashEntry? {
    if (!block.startsWith(BLOCK_HEADER)) return null
    val rest = block.removePrefix(BLOCK_HEADER).trimStart('\n')
    val breakAt = rest.indexOf('\n')
    val header = if (breakAt < 0) rest else rest.substring(0, breakAt)
    val detail = if (breakAt < 0) "" else rest.substring(breakAt + 1)
    // 摘要里可能带 |，只切前两段
    val parts = header.split('|', limit = 3)
    if (parts.size < 3) return null
    val kind = CrashKind.entries.firstOrNull { it.name == parts[0] } ?: return null
    val time = parts[1].toLongOrNull() ?: return null
    return CrashEntry(kind, time, parts[2], detail)
}

private fun singleLine(text: String): String =
    text.replace('\n', ' ').replace('\r', ' ').replace('|', '/').trim()
