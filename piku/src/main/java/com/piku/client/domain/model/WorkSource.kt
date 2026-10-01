package com.piku.client.domain.model

enum class WorkSource {
    POIPIKU,
    PIXIV,
}

/**
 * 跨源作品身份：源 + 站内 id，收藏/历史等本地数据的键。各源 id 是独立命名空间
 * （poipiku 作品 123 与 pixiv 插图 123 是两个作品），新源加入枚举即自动获得独立键空间。
 * workId 里还编了 [WorkKind]：pixiv 的小说与插画 id 同域但不同序列，不带类型会撞号。
 */
data class WorkKey(val source: WorkSource, val workId: String) {
    /** "POIPIKU:123"：稳定、可读，LazyColumn 的 item key 直接用（String 可保存态） */
    override fun toString(): String = "${source.name}:$workId"
}

/** [Work] 的键；workId 用字符串承接，与收藏/历史实体列同型 */
val Work.key: WorkKey
    get() = WorkKey(source, kind.encode(id))
