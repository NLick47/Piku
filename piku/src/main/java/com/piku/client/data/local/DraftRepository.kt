package com.piku.client.data.local

import android.content.Context
import android.net.Uri
import android.util.Log
import com.piku.client.domain.model.NsfwLevel
import com.piku.client.domain.model.PublishDraft
import com.piku.client.domain.model.ShowVisibility
import com.piku.client.domain.model.UploadKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DraftRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DraftDao,
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

    suspend fun createEmpty(kind: UploadKind): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.insert(
            DraftEntity(kind = kind.ordinal, createdAt = now, updatedAt = now),
        )
    }

    /** 保存/更新草稿（密码由调用方提前清空，不落盘） */
    suspend fun save(draft: PublishDraft): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val prev = draft.draftId?.let { dao.byId(it) }
        val id = when {
            prev != null -> {
                dao.update(draft.toEntity(prev.id, createdAt = prev.createdAt, updatedAt = now))
                prev.id
            }
            draft.draftId != null ->
                dao.insert(draft.toEntity(draft.draftId, createdAt = now, updatedAt = now))
            else -> dao.insert(
                DraftEntity(kind = draft.kind.ordinal, createdAt = now, updatedAt = now),
            ).also { newId ->
                dao.update(draft.toEntity(newId, createdAt = now, updatedAt = now))
            }
        }
        syncImages(id, draft.imageFiles)
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
        rows.map { toDraft(it, dao.imagesFor(it.id).sortedBy { img -> img.sortOrder }.map { img -> img.path }) }
    }

    /** 读取某份草稿；图片文件若已丢失则剔除引用并回写 */
    suspend fun load(id: Long): PublishDraft? = withContext(Dispatchers.IO) {
        val entity = dao.byId(id) ?: return@withContext null
        val paths = dao.imagesFor(id).sortedBy { it.sortOrder }.map { it.path }
        val alive = paths.filter { File(it).exists() }
        if (alive.size != paths.size) syncImages(id, alive)
        toDraft(entity, alive)
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
        runCatching { dao.clearImages(id) }
        deleteImagesOf(id)
    }

    suspend fun duplicate(id: Long): Long? = withContext(Dispatchers.IO) {
        val entity = dao.byId(id) ?: return@withContext null
        val paths = dao.imagesFor(id).sortedBy { it.sortOrder }.map { it.path }
        val now = System.currentTimeMillis()
        val newId = dao.insert(entity.copy(id = 0, createdAt = now, updatedAt = now))
        if (paths.isNotEmpty()) {
            val newDir = dirFor(newId)
            val newPaths = paths.mapNotNull { p ->
                val src = File(p)
                if (!src.exists()) return@mapNotNull null
                val dst = File(newDir, src.name)
                runCatching { src.copyTo(dst, overwrite = true) }.getOrNull()?.absolutePath
            }
            dao.insertImages(newPaths.mapIndexed { i, p -> DraftImageEntity(draftId = newId, path = p, sortOrder = i) })
        }
        newId
    }

    /** 图片行与传入路径同步（路径一致则跳过，避免每次打字都重写） */
    private suspend fun syncImages(draftId: Long, paths: List<String>) {
        val current = dao.imagesFor(draftId).sortedBy { it.sortOrder }.map { it.path }
        if (current == paths) return
        dao.clearImages(draftId)
        if (paths.isNotEmpty()) {
            dao.insertImages(paths.mapIndexed { i, p -> DraftImageEntity(draftId = draftId, path = p, sortOrder = i) })
        }
    }

    private fun toDraft(e: DraftEntity, imagePaths: List<String>): PublishDraft = PublishDraft(
        draftId = e.id,
        savedAt = e.updatedAt,
        kind = UploadKind.entries.getOrNull(e.kind) ?: UploadKind.ILLUST,
        categoryCd = e.categoryCd,
        tags = e.tags,
        description = e.description,
        publish = e.publish,
        nsfw = NsfwLevel.entries.firstOrNull { it.wire == e.nsfwWire } ?: NsfwLevel.ALL,
        visibility = ShowVisibility.entries.getOrNull(e.visibility) ?: ShowVisibility.ANYONE,
        password = e.password,
        showRecent = e.showRecent,
        showFirstOnly = e.showFirstOnly,
        title = e.title,
        body = e.body,
        novelDirection = e.novelDirection,
        imageFiles = imagePaths,
    )

    private fun PublishDraft.toEntity(id: Long, createdAt: Long, updatedAt: Long): DraftEntity = DraftEntity(
        id = id,
        kind = kind.ordinal,
        categoryCd = categoryCd,
        tags = tags,
        description = description,
        publish = publish,
        nsfwWire = nsfw.wire,
        visibility = visibility.ordinal,
        // 密码不落盘：调用方保存前已清空，这里不再额外处理
        password = password,
        showRecent = showRecent,
        showFirstOnly = showFirstOnly,
        title = title,
        body = body,
        novelDirection = novelDirection,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

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
