package com.piku.client.data.remote

internal data class ResolveTrace(
    val hostname: String,
    val elapsedMs: Long,
    val sources: Collection<SourceTrace>,
    val probes: Collection<ProbeTrace>,
    /** 最终采用的地址；没有任何可用地址时为 null */
    val winner: String?,
    /** 实际交给 OkHttp 的候选 */
    val routed: List<String>,
    /** 是否走的兜底路径（一个健康地址都没有） */
    val fallback: Boolean,
)

internal data class SourceTrace(
    val name: String,
    val answers: List<String>,
    /** 过滤掉缓刑期后真正参与探测的条数 */
    val candidates: Int,
    val elapsedMs: Long,
    /** 查询失败的原因（异常摘要）；查询成功而无记录时为 null */
    val detail: String? = null,
    /** 被竞速取消（别的来源先成功）：不是失败，不该当成 DoH 不可用 */
    val cancelled: Boolean = false,
)

internal data class ProbeTrace(
    val address: String,
    val via: String,
    val outcome: String,
    val tcpMs: Long?,
    val tlsMs: Long?,
    val detail: String?,
)

/** 当前解析状态：赢家与候选池里每个地址的健康度 */
internal data class HostStatus(
    val hostname: String,
    val winner: String?,
    val winnerAgeMs: Long?,
    val addresses: List<AddressStatus>,
)

internal data class AddressStatus(
    val address: String,
    val persisted: Boolean,
    val failures: Int,
    /** 缓刑剩余时间；不在缓刑期为 null */
    val retryInMs: Long?,
)

/** 赢家由哪些来源给出过；迟到的来源也算，回答「DoH 是不是也解析出了这个地址」 */
internal fun winnerSourcesOf(trace: ResolveTrace): List<String> =
    trace.winner?.let { winner -> trace.sources.filter { winner in it.answers }.map { it.name } }.orEmpty()
