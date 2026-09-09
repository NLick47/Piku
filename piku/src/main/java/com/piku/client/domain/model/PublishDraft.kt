package com.piku.client.domain.model

import kotlinx.serialization.Serializable

/** 作品类型：图片（ED=0）或 小说（ED=3） */
enum class UploadKind { ILLUST, NOVEL }

/**
 * 年龄分级。网页端是「非 NSFW」开关 + NSFW_VAL 单选(2 缓冲/4 R18/8 R18+)，
 * UI 合并成四档；[wire] 为对应 NSFW_VAL，全年龄走开关的"非 NSFW"态。
 */
enum class NsfwLevel(val wire: Int?) {
    ALL(null),
    CUSHION(2),
    R18(4),
    R18PLUS(8),
}

/**
 * 可见范围。网页端默认"任何人都可以查看"（OPTION_NO_CONDITIONAL_SHOW=true），
 * 限定后 SHOW_LIMIT_VAL：5 仅登录 / 6 仅关注者。推特系(7/12)与列表(13)不接入。
 */
enum class ShowVisibility { ANYONE, POIPIKU_LOGIN, FOLLOWER }

/**
 * 发布作品的全部表单内容（不含图片字节）。同时是本地草稿的持久化载荷：
 * 序列化成 JSON 存 Room，图片另拷贝到私有目录、[imageFiles] 记路径。
 */
@Serializable
data class PublishDraft(
    /** 草稿箱里的行 id；null = 尚未入箱（发布成功即不再需要） */
    val draftId: Long? = null,
    /** 最近一次存草稿的时刻（毫秒），供草稿列表显示；与 draftId(首次创建时刻) 区分 */
    val savedAt: Long? = null,
    val kind: UploadKind = UploadKind.ILLUST,
    val categoryCd: Int = 0,
    val tags: String = "",
    val description: String = "",
    val publish: Boolean = true,
    val nsfw: NsfwLevel = NsfwLevel.ALL,
    val visibility: ShowVisibility = ShowVisibility.ANYONE,
    val password: String = "",
    val showRecent: Boolean = true,
    val showFirstOnly: Boolean = false,
    val title: String = "",
    val body: String = "",
    val novelDirection: Int = 0,
    val imageFiles: List<String> = emptyList(),
) {
    /** 是否有实质内容（决定离开确认/草稿是否值得存）；纯开关翻转不算内容 */
    val hasContent: Boolean
        get() = when (kind) {
            UploadKind.ILLUST -> imageFiles.isNotEmpty() ||
                tags.isNotBlank() || description.isNotBlank() ||
                categoryCd != 0 || !publish || nsfw != NsfwLevel.ALL ||
                visibility != ShowVisibility.ANYONE || password.isNotBlank() ||
                showFirstOnly
            UploadKind.NOVEL -> title.isNotBlank() || body.isNotBlank() ||
                tags.isNotBlank() || description.isNotBlank() ||
                categoryCd != 0 || !publish || nsfw != NsfwLevel.ALL ||
                visibility != ShowVisibility.ANYONE || password.isNotBlank()
        }
}
