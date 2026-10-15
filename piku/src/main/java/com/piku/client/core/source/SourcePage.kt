package com.piku.client.core.source

import com.piku.client.core.model.Work

/**
 * 一页结果。**"还有没有下一页"只认 [hasNext]**（null = 源没给，外壳按"空页即到底"判定）；
 * [totalPages] 只服务展示（榜单名次/进度），**不得**参与"要不要发请求"的判定——
 * 两边混用会让"刷新"被当成"翻到了末页"而本地短路成空列表。
 */
data class SourcePage(
    val items: List<Work>,
    /** 站点自己给了下一页信号时填这里；不知道给 null */
    val hasNext: Boolean? = null,
    /** 已知总页数，null = 未知。展示用，判定到底不用它 */
    val totalPages: Int? = null,
    val nextCursor: String? = null,
)
