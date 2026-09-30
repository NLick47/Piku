package com.piku.client.domain.source

import com.piku.client.domain.model.WorkSource

interface SourceContentBackup {

    val source: WorkSource

    suspend fun content(work: BackupWork): ContentBackup?
}

/** 备份一件作品要用的最小信息，与同步协议解耦 */
data class BackupWork(
    val workId: String,
    val authorId: Long,
    val imageCount: Int,
)

/** 一件作品的全部可备份内容；两项都空 = 没东西可备份 */
data class ContentBackup(
    /** 原图优先，取不到原图退回详情页那一档 */
    val images: List<String> = emptyList(),
    val novelText: String = "",
)
