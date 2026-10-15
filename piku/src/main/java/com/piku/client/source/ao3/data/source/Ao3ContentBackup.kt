package com.piku.client.source.ao3.data.source

import com.piku.client.core.model.WorkKind
import com.piku.client.core.model.WorkSource
import com.piku.client.core.model.decodeWorkId
import com.piku.client.core.source.BackupWork
import com.piku.client.core.source.ContentBackup
import com.piku.client.core.source.SourceContentBackup
import com.piku.client.source.ao3.data.repository.Ao3Repository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Ao3ContentBackup @Inject constructor(
    private val repository: Ao3Repository,
) : SourceContentBackup {

    override val source = WorkSource.AO3

    override suspend fun content(work: BackupWork): ContentBackup? {
        val id = ao3WorkIdFromBackupKey(work.workId) ?: return null
        val text = repository.workDetail(id, full = true).getOrNull()?.fullText.orEmpty()
        return text.takeIf { it.isNotBlank() }?.let { ContentBackup(novelText = it) }
    }
}

/** 备份键是 WorkKind 编码过的（AO3 一律小说 → `n` 前缀），解出站内 id 才问得动接口 */
internal fun ao3WorkIdFromBackupKey(key: String): Long? {
    val (id, kind) = decodeWorkId(key)
    return id.takeIf { kind == WorkKind.NOVEL && it > 0L }
}
