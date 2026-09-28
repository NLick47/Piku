package com.piku.client.data.remote

import com.piku.client.domain.model.ImageRouteMode
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NetworkDiagnosis internal constructor(
    private val dns: DoHDns,
    private val diagnostics: NetworkDiagnostics,
    private val prober: AddressProbe,
    private val runtime: NetworkRuntime,
    private val imageProbe: ImageRouteProbe,
    private val routeController: ImageRouteController,
    private val imageDiagnostics: ImageDiagnostics,
) {

    /** 生产构造：真实 TLS 与图片线路探测 + 墙钟时间 */
    constructor(
        dns: DoHDns,
        diagnostics: NetworkDiagnostics,
        imageProbe: ImageRouteProbe,
        routeController: ImageRouteController,
        imageDiagnostics: ImageDiagnostics,
    ) : this(
        dns,
        diagnostics,
        TlsAddressProbe(),
        NetworkRuntime(),
        imageProbe,
        routeController,
        imageDiagnostics,
    )

    fun report(appVersion: String, environment: NetworkEnvironment, live: Boolean): String {
        val deadline = runtime.now() + LIVE_BUDGET_MS
        val liveLines = if (live) liveProbe(deadline) else emptyList()
        val imageLiveLines = if (live) liveImageProbe(deadline) else emptyList()
        return buildString {
            appendLine("Piku $appVersion 网络诊断")
            appendLine("生成时间 ${stamp(runtime.now())}")
            appendLine()
            appendLine("—— 环境 ——")
            val state = if (environment.validated) "已验证" else "未验证"
            val metered = if (environment.metered) "  计费" else ""
            appendLine("网络 ${environment.transport}（$state）$metered")
            appendLine("系统 DNS ${environment.systemDns.joinToString(", ").ifEmpty { "无" }}")
            appendLine("最近连接协议 ${diagnostics.lastProtocol ?: "未知"}")
            appendLine()
            appendLine("—— 解析链路（最近一次）——")
            DoHDns.BUSINESS_HOSTS.forEach { host -> appendLine(hostTrace(host)) }
            if (liveLines.isNotEmpty()) {
                appendLine()
                appendLine("—— 实时探测（本次，各候选逐个 TCP+TLS）——")
                liveLines.forEach { appendLine(it) }
            }
            appendLine()
            appendLine("—— 图片线路 ——")
            imageRouteState().forEach { appendLine(it) }
            if (imageLiveLines.isNotEmpty()) {
                appendLine()
                appendLine("—— 图片线路（实时探测）——")
                imageLiveLines.forEach { appendLine(it) }
            }
            appendLine()
            appendLine("—— 图片请求（网络层，本次进程）——")
            imageDiagnostics.summary().forEach { appendLine(it) }
            appendLine()
            appendLine("—— 当前状态 ——")
            DoHDns.BUSINESS_HOSTS.forEach { host -> appendLine(hostStatus(host)) }
            val events = diagnostics.eventLines()
            appendLine()
            appendLine("—— 事件（最新在前，共 ${events.size} 条）——")
            if (events.isEmpty()) {
                appendLine("  （空：清空只清这里，上面的解析与线路状态是实时快照）")
            } else {
                events.forEach { appendLine(it) }
            }
        }
    }

    /** 强制重新解析 + 对每个候选做一轮带计时的探测；总预算 [LIVE_BUDGET_MS] 到点就停 */
    private fun liveProbe(deadline: Long): List<String> {
        val lines = mutableListOf<String>()
        DoHDns.BUSINESS_HOSTS.forEach { host ->
            dns.forceReResolve(host)
            runCatching { dns.lookup(host) }
            dns.status(host).addresses.forEach { entry ->
                if (runtime.now() > deadline) {
                    lines += "  $host ${entry.address}  未探测（超出预算）"
                    return@forEach
                }
                val address = runCatching { InetAddress.getByName(entry.address) }.getOrNull() ?: return@forEach
                val report = prober.probe(host, address)
                val stages = buildString {
                    append("TCP ")
                    append(report.tcpMs?.let { "${it}ms" } ?: "超时")
                    if (report.tlsMs != null) {
                        append("  TLS ${report.tlsMs}ms")
                    }
                }
                val detail = report.detail?.let { "（$it）" }.orEmpty()
                lines += "  $host ${entry.address}  $stages  ${outcomeText(report.outcome)}$detail"
            }
        }
        return lines
    }

    /**
     * 图片取源线路现状：模式、当前走哪边、最近一次判定的依据与是否生效、启动探测详情、
     * 以及每条中转线路的健康（优先级 / 累计失败 / 最近成功）。
     */
    private fun imageRouteState(): List<String> {
        val lines = mutableListOf<String>()
        val mode = when (routeController.mode) {
            ImageRouteMode.AUTO -> "自动（AUTO）"
            ImageRouteMode.DIRECT -> "强制直连"
            ImageRouteMode.RELAY -> "强制中转"
        }
        lines += "模式 $mode   当前 ${if (routeController.useRelay) "走中转" else "走直连"}"
        routeController.lastDecision?.let { decision ->
            val target = if (decision.relay) "中转" else "直连"
            val state = if (decision.applied) "已生效" else "被冷却拦下（20s 内不重复翻转）"
            lines += "  最近判定 切到$target｜${decision.reason}｜${stamp(decision.atMillis)}｜$state"
        }
        routeController.lastProbe?.let { probe ->
            val detail = probeDetail(probe)
            val verdict = if (probe.ok) "直连可用" else "直连不可用"
            lines += "  启动探测 $verdict｜${probe.elapsedMs}ms$detail｜${stamp(probe.atMillis)}"
        }
        lines += "  中转线路"
        routeController.relayHealth.states().forEachIndexed { index, state ->
            val health = if (state.failures > 0) "健康（累计失败 ${state.failures} 次）" else "健康"
            val lastSuccess = state.lastSuccessAt?.let { "，最近成功 ${stamp(it)}" }.orEmpty()
            lines += "    ${state.host}  第 ${index + 1} 位  $health$lastSuccess"
        }
        if (routeController.mode != ImageRouteMode.AUTO) {
            lines += "  手动模式下探测与失败信号都不改变线路选择"
        }
        return lines
    }

    /** 直连与每条中转各探一次：直连的结论会像启动探测一样参与自动判定，中转只作展示 */
    private fun liveImageProbe(deadline: Long): List<String> {
        val lines = mutableListOf<String>()
        val direct = imageProbe.probe(host = null)
        routeController.applyProbe(direct)
        lines += "  直连 ${ImageRelayInterceptor.CDN_HOST}  ${probeLine(direct)}"
        routeController.relayHealth.states().forEach { state ->
            if (runtime.now() > deadline) {
                lines += "  中转 ${state.host}  未探测（超出预算）"
                return@forEach
            }
            lines += "  中转 ${state.host}  ${probeLine(imageProbe.probe(state.host))}"
        }
        return lines
    }

    /** 失败带异常摘要、成功带状态码与耗时 */
    private fun probeDetail(probe: ImageProbeResult): String = when {
        probe.error != null -> "（${probe.error}）"
        probe.statusCode != null -> "（HTTP ${probe.statusCode}）"
        else -> ""
    }

    private fun probeLine(probe: ImageProbeResult): String =
        (if (probe.ok) "可用" else "不可用") + " ${probe.elapsedMs}ms" + probeDetail(probe)

    private fun hostTrace(host: String): String {
        val trace = dns.lastTrace(host)
            ?: return "$host  本次进程内尚未重新解析（赢家仍在有效期内）"
        return buildString {
            appendLine("$host  总耗时 ${trace.elapsedMs}ms" + if (trace.fallback) "  兜底路径" else "")
            if (trace.sources.isEmpty()) {
                appendLine("  来源: 无（未重新查询）")
            } else {
                appendLine("  来源:")
                trace.sources.forEach { source ->
                    val summary = when {
                        source.answers.isNotEmpty() -> source.answers.joinToString(" ")
                        source.cancelled -> "已取消（其他来源先返回，本次没有结论）"
                        source.detail != null -> "查询失败（${source.detail}）"
                        else -> "查询成功但没有记录"
                    }
                    appendLine("    ${source.name}  ${source.answers.size} 条  ${source.elapsedMs}ms  $summary")
                }
            }
            if (trace.probes.isNotEmpty()) {
                appendLine("  探测:")
                trace.probes.forEach { probe ->
                    val stages = buildString {
                        append("TCP ")
                        append(probe.tcpMs?.let { "${it}ms" } ?: "—")
                        if (probe.tlsMs != null) append("  TLS ${probe.tlsMs}ms")
                    }
                    val detail = probe.detail?.let { "（$it）" }.orEmpty()
                    appendLine(
                        "    ${probe.address}  来自${probe.via}  $stages  ${probe.outcome}$detail",
                    )
                }
            }
            appendLine(
                if (trace.winner != null) {
                    val from = winnerSourcesOf(trace).joinToString("、").ifEmpty { "未知" }
                    "  采用 ${trace.winner}（来自 $from）"
                } else {
                    "  采用 无可用地址"
                },
            )
            appendLine("  交给 OkHttp ${trace.routed.joinToString(", ").ifEmpty { "无" }}")
            verdictOf(trace)?.let { appendLine("  判定 $it") }
        }
    }

    private fun hostStatus(host: String): String = buildString {
        val status = dns.status(host)
        appendLine(
            if (status.winner != null) {
                "$host  赢家 ${status.winner}（${status.winnerAgeMs ?: 0}ms 前验证）"
            } else {
                "$host  无赢家（缓刑中或尚未解析）"
            },
        )
        status.addresses.forEach { entry ->
            val health = when {
                entry.retryInMs != null -> "缓刑剩 ${entry.retryInMs}ms（失败 ${entry.failures} 次）"
                entry.failures > 0 -> "健康（失败 ${entry.failures} 次后恢复）"
                else -> "健康"
            }
            appendLine("  ${entry.address}  ${if (entry.persisted) "持久化" else "本次解析"}  $health")
        }
    }

    private fun outcomeText(outcome: ProbeOutcome): String = when (outcome) {
        ProbeOutcome.OK -> "通过"
        ProbeOutcome.CONNECT_FAILED -> "连接失败"
        ProbeOutcome.TLS_FAILED -> "握手/证书失败"
    }

    private fun stamp(millis: Long): String =
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(millis))

    private companion object {
        /** 实时探测的总预算：逐条探测最坏各占 5s，超预算的条目照实标注 */
        const val LIVE_BUDGET_MS = 8_000L
    }
}

/**
 * 由探测现象给出排查方向：只描述观察到的现象与常见原因，不下结论。
 *
 * 分阶段的价值就在这里——TCP 通而握手不过，与 TCP 就通不了，是完全不同的两类故障。
 */
internal fun verdictOf(trace: ResolveTrace): String? {
    if (trace.probes.isEmpty()) return null
    val refused = trace.probes.count { it.outcome == "连接失败" }
    val handshakeFailed = trace.probes.count { it.outcome == "握手/证书失败" }
    return when {
        refused > 0 && handshakeFailed > 0 ->
            "既有连不上的地址、也有握手过不去的：后者常见于 SNI 阻断或中间设备重置"

        handshakeFailed > 0 ->
            "TCP 能连上但握手过不去，常见于 SNI 阻断、中间设备重置，或该 IP 已不是本站点"

        refused == trace.probes.size ->
            "DNS 有答案但候选地址全部连不上，常见于地址被墙或 DoH 返回了不可达的 IP"

        trace.winner == null -> "DNS 有答案但都探测失败（未拿到可用地址）"

        trace.sources.any { it.name != DoHDns.SYSTEM_SOURCE_NAME && it.detail != null && !it.cancelled } ->
            "DoH 来源有查询失败（见来源明细）：本次解析只靠系统 DNS，而系统 DNS 被污染的网络下没有兜底"

        else -> null
    }
}
