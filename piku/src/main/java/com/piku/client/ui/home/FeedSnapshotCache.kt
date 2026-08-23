package com.piku.client.ui.home

import com.piku.client.domain.model.PoipikuCategory
import com.piku.client.domain.model.Work

/** 一份列表的快照：切回对应 tab/分类/标签时直接恢复，免去重复网络请求 */
internal data class FeedSnapshot(
    val works: List<Work>,
    val page: Int,
    val endReached: Boolean,
)

/** 一份列表由 (tab, 分类, 标签) 唯一确定 */
internal data class FeedKey(
    val tab: FeedTab,
    val category: PoipikuCategory,
    val tag: String?,
)

/**
 * 首页列表内存快照缓存（LRU 限容）。
 *
 * 线程约定：仅主线程访问（与 HomeViewModel 的 viewModelScope 一致）。
 * [updateThumbnail] 采用“先收集匹配项、迭代结束后统一写回”，
 * 绝不在遍历 map 期间 put——accessOrder 模式下 put 已有 key 会重排链表并
 * 递增 modCount，边遍历边写会抛 ConcurrentModificationException（历史 bug）。
 */
internal class FeedSnapshotCache(private val maxSize: Int = DEFAULT_MAX_ENTRIES) {

    private val map = object : LinkedHashMap<FeedKey, FeedSnapshot>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FeedKey, FeedSnapshot>): Boolean =
            size > maxSize
    }

    operator fun get(key: FeedKey): FeedSnapshot? = map[key]

    operator fun set(key: FeedKey, snapshot: FeedSnapshot) {
        map[key] = snapshot
    }

    fun clear() = map.clear()

    /** 把缓存里所有 [workId] 匹配作品的缩略图替换为 [thumbnailUrl]，无匹配则不动 */
    fun updateThumbnail(workId: Long, thumbnailUrl: String) {
        val updates = mutableListOf<Pair<FeedKey, FeedSnapshot>>()
        for ((key, snap) in map) {
            if (snap.works.none { it.id == workId && it.thumbnailUrl != thumbnailUrl }) continue
            updates += key to snap.copy(
                works = snap.works.map {
                    if (it.id == workId && it.thumbnailUrl != thumbnailUrl) {
                        it.copy(thumbnailUrl = thumbnailUrl)
                    } else {
                        it
                    }
                },
            )
        }
        for ((key, snap) in updates) map[key] = snap
    }

    companion object {
        /** tab×分类×标签组合有限，12 个足够且内存有界 */
        const val DEFAULT_MAX_ENTRIES = 12
    }
}
