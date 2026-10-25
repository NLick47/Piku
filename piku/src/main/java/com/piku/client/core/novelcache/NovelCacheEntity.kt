package com.piku.client.core.novelcache

import androidx.room.Entity
import androidx.room.Index

/**
 * 正文缓存的作品级元数据。独立库（novel_cache.db），不进主库：
 * 缓存可丢、体积大，不能跟着系统云备份上传，也不该拖累收藏/历史的迁移链。
 */
@Entity(
    tableName = "novel_cache",
    primaryKeys = ["source", "workId"],
    indices = [Index("fetchedAt")],
)
data class NovelCacheEntity(
    val source: String,
    /** WorkKind 编码过的站内 id：pixiv 小说 n123 与插图 123 不撞 */
    val workId: String,
    /** 管理页「查看作品」要用：poipiku 的详情地址按作者 id 拼 */
    val authorId: Long,
    val title: String,
    val authorName: String,
    val chapterCount: Int,
    val charCount: Int,
    val byteSize: Long,
    val fetchedAt: Long,
    val formatVersion: Int,
)

/** 章节表不用外键：删作品时由 [NovelCacheStore] 在事务里先删章再删 meta */
@Entity(
    tableName = "novel_chapters",
    primaryKeys = ["source", "workId", "ordinal"],
)
data class NovelChapterEntity(
    val source: String,
    val workId: String,
    val ordinal: Int,
    val title: String,
    val text: String,
    /** 章标题 + 正文的 SHA-256：合并时只比它，旧章不重算 */
    val contentHash: String,
    val charCount: Int,
    val byteSize: Long,
)
