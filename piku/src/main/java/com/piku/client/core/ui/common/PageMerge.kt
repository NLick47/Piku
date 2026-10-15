package com.piku.client.core.ui.common

/**
 * 追加分页结果并按 [keyOf] 去重。站点分页会飘（同一个作品/标签出现在相邻页），
 * 列表 item key 撞车时 Compose 直接抛 IllegalArgumentException——滑到那儿就崩。
 */
internal fun <T> mergePage(current: List<T>, incoming: List<T>, keyOf: (T) -> Any): List<T> {
    val seen = current.mapTo(mutableSetOf(), keyOf)
    return current + incoming.filter { seen.add(keyOf(it)) }
}
