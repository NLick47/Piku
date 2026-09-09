package com.piku.client.data.local

import android.content.Context
import android.net.Uri
import android.util.Log
import com.piku.client.domain.model.PublishDraft
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 发布草稿仓库（多份草稿箱）。
 *
 * 每份草稿一个独立目录 drafts/<id>/，图片在选中/恢复那一刻拷贝进该目录
 * （Photo Picker 的 URI 授权不跨重启，所以必须在编辑期落盘）。
 * payload 记图片绝对路径；删除草稿/丢弃编辑 = 删行 + 删整目录。
 */
@Singleton
class DraftRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DraftWorkDao,
    private val json: Json,
) {
    private val draftsDir = File(context.filesDir, "drafts")

    /** 某份草稿的专属图片目录，按需创建 */
    fun dirFor(draftId: Long): File {
        val dir = File(draftsDir, "$DIR_PREFIX$draftId")
        dir.mkdirs()
        return dir
    }

    /** 选中图片 → 拷入该草稿目录，返回本地文件（格式不支持/失败返回 null） */
    fun copyImage(draftId: Long, uri: Uri): File? = runCatching {
        val ext = extOf(context.contentResolver.getType(uri)) ?: return null
        val dir = dirFor(draftId)
        val target = File(dir, "img_${System.currentTimeMillis()}_${(0..999).random()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        target
    }.getOrElse {
        Log.w(TAG, "copy image failed: $it")
        null
    }

    fun imageType(uri: Uri): String? = context.contentResolver.getType(uri)

    /** 编辑中删除某张图：同步删本地拷贝 */
    fun deleteImage(file: File) {
        runCatching { file.delete() }
    }

    /** 丢弃尚未成稿的编辑：整目录删除 */
    fun deleteImagesOf(draftId: Long) {
        runCatching { File(draftsDir, "$DIR_PREFIX$draftId").deleteRecursively() }
    }

    /** 保存/更新草稿（upsert 按 id，客户端分配时间戳 id） */
    suspend fun save(draft: PublishDraft): Long = withContext(Dispatchers.IO) {
        val id = draft.draftId ?: System.currentTimeMillis()
        val now = System.currentTimeMillis()
        dao.upsert(
            DraftWorkEntity(
                id = id,
                payload = json.encodeToString(
                    PublishDraft.serializer(),
                    draft.copy(draftId = id, savedAt = now),
                ),
                updatedAt = now,
            ),
        )
        id
    }

    /** 全部草稿（新→旧），顺带清理没有对应 DB 行的孤儿图片目录（进程被杀等残留） */
    suspend fun drafts(): List<PublishDraft> = withContext(Dispatchers.IO) {
        val rows = dao.all()
        val keep = rows.mapTo(mutableSetOf()) { it.id }
        draftsDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                dir.name.removePrefix(DIR_PREFIX).toLongOrNull()?.let { id ->
                    if (id !in keep) runCatching { dir.deleteRecursively() }
                }
            }
        }
        rows.mapNotNull { decodeEntity(it) }
    }

    /** 继续编辑某份草稿；图片文件若已丢失则剔除引用 */
    suspend fun load(id: Long): PublishDraft? = withContext(Dispatchers.IO) {
        val entity = dao.byId(id) ?: return@withContext null
        val draft = decodeEntity(entity) ?: return@withContext null
        val alive = draft.imageFiles.filter { File(it).exists() }
        if (alive.size != draft.imageFiles.size) {
            save(draft.copy(draftId = id, imageFiles = alive))
        }
        draft.copy(draftId = id, imageFiles = alive)
    }

    private fun decodeEntity(entity: DraftWorkEntity): PublishDraft? = runCatching {
        json.decodeFromString(PublishDraft.serializer(), entity.payload)
    }.getOrNull()

    /** 删除一份草稿（行 + 图片目录） */
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
        deleteImagesOf(id)
    }

    private fun extOf(type: String?): String? = when (type?.lowercase()) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        else -> null
    }

    private companion object {
        const val TAG = "PikuDiag"
        const val DIR_PREFIX = "draft_"
    }
}
