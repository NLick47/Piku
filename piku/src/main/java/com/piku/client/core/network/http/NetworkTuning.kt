package com.piku.client.core.network.http

internal object NetworkTuning {

    /**
     * 一次 lookup 交给 OkHttp 的候选上限。每条候选失败都要吃满一个连接超时才会轮到
     * 下一条，所以 [MAX_ROUTES] × [CONNECT_TIMEOUT_MS] 必须留在 [MANAGED_ROUTE_BUDGET_MS]
     * 之内（2 × 5s = 10s < 15s）；3 条正好撞满 15s，会把常规线路的预算吃光。
     */
    const val MAX_ROUTES = 2

    /** 兜底路径（没有任何健康地址）只给一条：多带候选会把失败时间乘以条数 */
    const val MAX_FALLBACK_ROUTES = 1

    const val CONNECT_TIMEOUT_MS = 5_000L
    const val READ_TIMEOUT_MS = 30_000L
    const val WRITE_TIMEOUT_MS = 30_000L

    /** 图片 client 的总超时：原生通道要把地址池逐个试完，比常规线路的预算宽 */
    const val IMAGE_CALL_TIMEOUT_MS = 30_000L

    /** 常规线路（直连/中继）自己的预算：比总超时更紧，避免坏网下白等到最后才切原生 */
    const val MANAGED_ROUTE_BUDGET_MS = 15_000L

    /** 启动探测的总超时：与首屏并行，不能拖着启动路径 */
    const val PROBE_CALL_TIMEOUT_MS = 5_000L
}
