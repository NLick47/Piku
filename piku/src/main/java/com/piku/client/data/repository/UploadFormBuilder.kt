package com.piku.client.data.repository

import com.piku.client.domain.model.NsfwLevel
import com.piku.client.domain.model.PublishDraft
import com.piku.client.domain.model.ShowVisibility
import com.piku.client.domain.model.UploadKind
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File

/**
 * 发布请求体构建。纯 JVM 可单测。
 *
 * 与网页端 UploadFilePcV2.js 对齐的约定：
 * - 布尔一律传 "true"/"false" 字符串（jQuery 序列化习惯）；
 * - NSFW_VAL / SHOW_LIMIT_VAL / PASSWORD_VAL 只在对应"指定/限定"开关打开时才携带；
 * - REC 与 OPTION_RECENT **取反**（进最新动向 → REC=0）。
 */
object UploadFormBuilder {

    /**
     * Step1 建条目 / 小说发布共用的公共字段（ED 按 [draft.kind] 区分，
     * 小说额外带 TIT/BDY/NOVEL_DIRECTION_VAL）。
     */
    fun entryForm(draft: PublishDraft, uid: Long, rid: Long = -1): FormBody {
        val ed = when (draft.kind) {
            UploadKind.ILLUST -> "0"
            UploadKind.NOVEL -> "3"
        }
        val notNsfw = draft.nsfw == NsfwLevel.ALL
        val noConditional = draft.visibility == ShowVisibility.ANYONE
        val noPassword = draft.password.isBlank()

        val builder = FormBody.Builder()
        builder.add("ED", ed)
        builder.add("UID", uid.toString())
        builder.add("GD", "1")
        builder.add("CAT", draft.categoryCd.toString())
        builder.add("TAG", draft.tags)
        builder.add("RID", rid.toString())
        builder.add("NOTE", "")
        builder.add("OPTION_PUBLISH", bool(draft.publish))
        builder.add("OPTION_NOT_TIME_LIMITED", "true")
        builder.add("OPTION_NOT_PUBLISH_NSFW", bool(notNsfw))
        if (!notNsfw) {
            builder.add("NSFW_VAL", draft.nsfw.wire.toString())
        }
        builder.add("OPTION_NO_CONDITIONAL_SHOW", bool(noConditional))
        if (!noConditional) {
            builder.add("SHOW_LIMIT_VAL", draft.visibility.showLimitValue().toString())
        }
        builder.add("OPTION_NO_PASSWORD", bool(noPassword))
        if (!noPassword) {
            builder.add("PASSWORD_VAL", draft.password)
        }
        builder.add("OPTION_SHOW_FIRST", bool(draft.showFirstOnly))
        builder.add("OPTION_RECENT", bool(draft.showRecent))
        builder.add("OPTION_TWEET", "false")
        builder.add("OPTION_TWEET_IMAGE", "true")
        builder.add("OPTION_TWITTER_CARD_THUMBNAIL", "true")
        builder.add("DES", draft.description)
        for (i in 0..8) builder.add("DES$i", "")
        builder.add("ITEM_BG_COLOR", "")
        if (draft.kind == UploadKind.NOVEL) {
            builder.add("TIT", draft.title)
            builder.add("BDY", draft.body)
            builder.add("NOVEL_DIRECTION_VAL", draft.novelDirection.toString())
        }
        return builder.build()
    }

    /**
     * 图片上传体（第一张或追加共用一个结构）：
     * UID / IID / OID / REC + qqfile。REC 与 [PublishDraft.showRecent] 取反。
     * [onProgress] 以整文件字节粒度回调，供 UI 画单张进度。
     */
    fun imageUploadBody(
        draft: PublishDraft,
        uid: Long,
        iid: Long,
        oid: Long,
        file: File,
        onProgress: ((written: Long, total: Long) -> Unit)? = null,
    ): MultipartBody {
        val rec = if (draft.showRecent) "0" else "1"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("UID", uid.toString())
            .addFormDataPart("IID", iid.toString())
            .addFormDataPart("OID", oid.toString())
            .addFormDataPart("REC", rec)
            .addFormDataPart("qqfile", file.name, fileBody(file, onProgress))
            .build()
        return body
    }

    /** 删除作品（放弃半成品草稿条目） */
    fun deleteForm(uid: Long, contentId: Long): FormBody =
        FormBody.Builder()
            .add("UID", uid.toString())
            .add("CID", contentId.toString())
            .add("DELTW", "0")
            .build()

    private fun fileBody(file: File, onProgress: ((written: Long, total: Long) -> Unit)?): RequestBody =
        object : RequestBody() {
            override fun contentType(): MediaType? = guessImageType(file)

            override fun contentLength(): Long = file.length()

            override fun writeTo(sink: BufferedSink) {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var written = 0L
                file.inputStream().buffered().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        written += read
                        onProgress?.invoke(written, contentLength())
                    }
                }
            }
        }

    private fun guessImageType(file: File): MediaType = when (file.extension.lowercase()) {
        "png" -> "image/png".toMediaType()
        "gif" -> "image/gif".toMediaType()
        "webp" -> "image/webp".toMediaType()
        "jpeg" -> "image/jpeg".toMediaType()
        else -> "image/jpeg".toMediaType()
    }

    private fun bool(v: Boolean): String = if (v) "true" else "false"

    private fun ShowVisibility.showLimitValue(): Int = when (this) {
        ShowVisibility.POIPIKU_LOGIN -> 5
        ShowVisibility.FOLLOWER -> 6
        ShowVisibility.ANYONE -> error("unreachable")
    }

    private const val DEFAULT_BUFFER_SIZE = 64 * 1024
}
