package com.piku.client.core.diag

import android.app.ApplicationExitInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportTest {

    private val device = DeviceInfo(
        appVersion = "3.4.0",
        android = "14（SDK 34）",
        model = "Xiaomi 2201123C",
        rom = "SKQ1.230401.001",
        abis = "arm64-v8a",
    )

    private fun entry(
        kind: CrashKind = CrashKind.JAVA,
        time: Long = 1_730_000_000_000L,
        headline: String = "headline",
        detail: String = "detail",
    ) = CrashEntry(kind, time, headline, detail)

    private fun deepStack(depth: Int): Throwable = runCatching {
        fun dive(n: Int): Int = if (n <= 0) error("deep") else dive(n - 1)
        dive(depth)
    }.exceptionOrNull()!!

    @Test
    fun reportPutsCrashSectionBeforeNetwork() {
        val report = buildDiagnosticsReport(
            device = device,
            stamp = "2026-10-15 13:20:00",
            crashSection = formatCrashSection(listOf(entry())),
            networkSection = "—— 环境 ——\n网络 WIFI（已验证）",
        )
        assertTrue(report.startsWith("Piku 3.4.0 诊断报告"))
        assertTrue(report.contains("生成时间 2026-10-15 13:20:00"))
        assertTrue(report.contains("设备 Xiaomi 2201123C / Android 14（SDK 34）"))
        assertTrue(report.contains("系统 SKQ1.230401.001 / arm64-v8a"))
        // 异常段必须排在网络段之前：网络那段上百行，压到后面没人看得见
        assertTrue(report.indexOf("—— 异常记录 ——") < report.indexOf("—— 环境 ——"))
    }

    @Test
    fun emptyCrashSectionSaysSoInsteadOfBlank() {
        val section = formatCrashSection(emptyList())
        assertEquals("—— 异常记录 ——\n本机暂无异常记录", section)
    }

    @Test
    fun entryShowsStampKindHeadlineAndDetail() {
        val text = formatEntry(entry(kind = CrashKind.NATIVE, headline = "原生崩溃: SIGSEGV", detail = "帧一\n帧二"))
        assertTrue(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}  原生崩溃""").containsMatchIn(text))
        assertTrue(text.contains("\n  原生崩溃: SIGSEGV\n"))
        assertTrue(text.endsWith("帧一\n帧二"))
    }

    @Test
    fun javaEntryKeepsClassAndMessageOnOneLine() {
        val recorded = javaEntry(IllegalStateException("boom\n第二行"), "main", 1L)
        assertEquals(CrashKind.JAVA, recorded.kind)
        assertEquals("java.lang.IllegalStateException: boom 第二行", recorded.headline)
        assertTrue(recorded.detail.startsWith("线程 main\n"))
    }

    @Test
    fun longStackIsTruncatedButKeepsTheTopFrames() {
        val recorded = javaEntry(deepStack(400), "main", 1L)
        assertTrue(recorded.detail.contains("已截断"))
        assertTrue(recorded.detail.length <= MAX_DETAIL_CHARS + 40)
        // 截断只砍尾巴：最上面的帧才是现场
        assertTrue(recorded.detail.substringBefore("已截断").contains("IllegalStateException: deep"))
    }

    @Test
    fun newestEntryFirstAndOldestDropped() {
        var merged = emptyList<CrashEntry>()
        (1..4).forEach { merged = mergeEntries(merged, entry(time = it.toLong(), headline = "c$it")) }
        assertEquals(listOf("c4", "c3", "c2"), merged.map { it.headline })
    }

    @Test
    fun entriesSurviveFileRoundTrip() {
        val entries = listOf(
            CrashEntry(CrashKind.NATIVE, 2L, "原生崩溃: SIGSEGV", "帧一\n帧二"),
            CrashEntry(CrashKind.JAVA, 1L, "java.lang.NullPointerException", "线程 main\n\tat A.b(C.kt:1)"),
        )
        assertEquals(entries, decodeEntries(encodeEntries(entries)))
    }

    @Test
    fun headlineIsFoldedToOneLineSoTheFormatHolds() {
        val decoded = decodeEntries(encodeEntries(listOf(entry(headline = "boom | 换\n行")))).single()
        assertEquals("boom / 换 行", decoded.headline)
    }

    @Test
    fun halfWrittenBlockIsDroppedAndTheRestSurvive() {
        val written = CrashEntry(CrashKind.ANR, 9L, "无响应 (ANR)", "trace")
        // 崩在写盘中途：块头都没写完，整块丢掉、前面那条照读
        assertEquals(listOf(written), decodeEntries(encodeEntries(listOf(written)) + "### PikuCrash v1\nNATIVE|10"))
        // 类型名不认识（格式对不上、文件被改坏）也只丢这一块，不能把整份记录带崩
        assertEquals(
            listOf(written),
            decodeEntries(encodeEntries(listOf(written)) + "### PikuCrash v1\n???|10|x\ndetail"),
        )
    }

    @Test
    fun fullyWrittenBlockIsKeptEvenWithoutTrailingSentinel() {
        val written = CrashEntry(CrashKind.ANR, 9L, "无响应 (ANR)", "trace")
        val tail = CrashEntry(CrashKind.LOW_MEMORY, 10L, "内存不足被杀", "描述")
        val text = encodeEntries(listOf(written)) + "\n" + encodeEntries(listOf(tail)).removeSuffix("@@end@@\n")
        assertEquals(listOf(written, tail), decodeEntries(text))
    }

    @Test
    fun onlyExitsJavaCannotSeeBecomeRecords() {
        assertEquals(CrashKind.NATIVE, exitKindForReason(ApplicationExitInfo.REASON_CRASH_NATIVE))
        assertEquals(CrashKind.ANR, exitKindForReason(ApplicationExitInfo.REASON_ANR))
        assertEquals(CrashKind.LOW_MEMORY, exitKindForReason(ApplicationExitInfo.REASON_LOW_MEMORY))
        assertEquals(
            CrashKind.INIT_FAILURE,
            exitKindForReason(ApplicationExitInfo.REASON_INITIALIZATION_FAILURE),
        )
        // Java 崩溃已由未捕获处理器记过一份；主动划掉、正常退出都不是异常
        assertNull(exitKindForReason(ApplicationExitInfo.REASON_CRASH))
        assertNull(exitKindForReason(ApplicationExitInfo.REASON_USER_REQUESTED))
        assertNull(exitKindForReason(ApplicationExitInfo.REASON_EXIT_SELF))
        assertNull(exitKindForReason(ApplicationExitInfo.REASON_OTHER))
    }

    @Test
    fun exitEntryFallsBackWhenSystemGivesNoDescription() {
        assertEquals("原生崩溃: SIGSEGV", exitEntry(CrashKind.NATIVE, 5L, "SIGSEGV", "tombstone").headline)
        assertEquals("原生崩溃", exitEntry(CrashKind.NATIVE, 5L, "   ", "tombstone").headline)
        assertEquals("原生崩溃", exitEntry(CrashKind.NATIVE, 5L, null, "tombstone").headline)
    }

    @Test
    fun binaryTombstoneKeepsOnlyReadableFragments() {
        // 真机 tombstone 就是这个形状：可读串之间夹着 protobuf 二进制
        val tombstone = "\u0000\u0001ab\u0002SIGSEGV\u0003\u0004\u0005 hwuiTask1 \u0008/lib64/libhwui.so\u0001"
        val detail = exitEntry(CrashKind.NATIVE, 5L, "SIGSEGV", tombstone).detail
        assertTrue(detail.contains("SIGSEGV"))
        assertTrue(detail.contains("hwuiTask1"))
        assertTrue(detail.contains("/lib64/libhwui.so"))
        // 太短的片段是二进制噪声，不留（"ab" 是故意塞的短串）
        assertFalse(detail.contains("ab"))
        assertFalse(detail.contains("\u0000"))
    }

    @Test
    fun textTracePassesThroughUnchanged() {
        val trace = "----- pid 123 at 2026-10-15 -----\n\"main\" prio=5 tid=1\n  at A.b(C.kt:1)\n"
        assertEquals(trace, readableRuns(trace))
    }

    @Test
    fun keptFragmentsGoOnTheirOwnLines() {
        // 二进制里丢掉的分隔符不能把两个片段粘成一行：库名与符号要能一行行扫过去
        assertEquals("SIGSEGV\nhwuiTask1", readableRuns("\u0000SIGSEGV\u0000hwuiTask1\u0000"))
    }
}
