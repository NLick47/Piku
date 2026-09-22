package com.piku.client.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 桌面装饰白名单条目。widget 只渲染这张表里的作品——
 * 数据流不经过收藏/历史/feed 大池子，服务端漏标的成人内容进不了桌面。
 *
 * 入库前的多信号快照（[r18]/[warning]/[adultLocked]/[passwordProtected]/[categoryCd]/[tags]）
 * 在添加那一刻固化：即便作品日后被作者改级，用户也能在装饰管理页凭这些字段复核。
 */
@Entity(tableName = "decoration_items")
data class DecorationItem(
    @PrimaryKey val workId: Long,
    val authorId: Long,
    val title: String,
    val authorName: String,
    /** App 私有目录（filesDir/decoration/）里的图片文件名，widget 离线渲染用 */
    val fileName: String,
    val addedAt: Long,
    // ---- 入库时刻的安全信号快照 ----
    val r18: Boolean,
    val warning: Boolean,
    val adultLocked: Boolean,
    val passwordProtected: Boolean,
    val categoryCd: Int,
    /** 详情页标签，逗号拼接；用户标签黑名单在此之上二次把关 */
    val tags: String,
)
